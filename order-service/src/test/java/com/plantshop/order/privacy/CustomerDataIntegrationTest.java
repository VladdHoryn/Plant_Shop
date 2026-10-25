package com.plantshop.order.privacy;

import com.plantshop.order.config.InternalApiTokenFilter;
import com.plantshop.order.entity.Order;
import com.plantshop.order.entity.OrderItem;
import com.plantshop.order.entity.PaymentMethod;
import com.plantshop.order.repository.OrderRepository;
import com.plantshop.order.service.CustomerDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The order part of the erasure workflow against a real PostgreSQL. Synthetic data only. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CustomerDataIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String EMAIL = "erin.erase@example.test";
    private static final String ADDRESS = "5 Cactus Lane, Odesa";
    private static final String OTHER_EMAIL = "other.customer@example.test";
    /** A report that must give the same result before and after an erasure. */
    private static final String REVENUE_REPORT =
            "select status, count(*) as orders, sum(total_amount) as revenue from orders.orders group by status order by status";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Value("${plantshop.internal-api.token}")
    private String token;

    @BeforeEach
    void seedOrders() {
        orderRepository.deleteAll();
        orderRepository.save(order(EMAIL, ADDRESS, "30.00"));
        // same customer, different spelling of the e-mail
        orderRepository.save(order(EMAIL.toUpperCase(), ADDRESS, "12.50"));
        orderRepository.save(order(OTHER_EMAIL, "9 Other Road, Lviv", "99.00"));
    }

    @Test
    void anonymizesOnlyThisCustomersOrdersAndKeepsThemUsable() throws Exception {
        List<Map<String, Object>> reportBefore = jdbc.queryForList(REVENUE_REPORT);

        anonymize(EMAIL, token).andExpect(status().isOk()).andExpect(jsonPath("$.ordersAnonymized").value(2));

        // no trace of the customer is left in the orders
        assertThat(jdbc.queryForObject("select count(*) from orders.orders where customer_email ilike ? or shipping_address = ?",
                Integer.class, EMAIL, ADDRESS)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from orders.orders where customer_email = ? and shipping_address = ?",
                Integer.class, CustomerDataService.ERASED_EMAIL, CustomerDataService.ERASED_ADDRESS)).isEqualTo(2);
        // the retained records are complete: same orders, same items, same report, no orphans
        assertThat(orderRepository.count()).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from orders.order_items i left join orders.orders o on o.id = i.order_id "
                + "where o.id is null", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from orders.order_items", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForList(REVENUE_REPORT)).isEqualTo(reportBefore);
        // the other customer is untouched
        assertThat(orderRepository.findByCustomerEmailIgnoreCaseOrderByCreatedAtDesc(OTHER_EMAIL))
                .singleElement().extracting(Order::getShippingAddress).isEqualTo("9 Other Road, Lviv");
    }

    @Test
    void repeatingTheAnonymizationIsSafe() throws Exception {
        anonymize(EMAIL, token).andExpect(jsonPath("$.ordersAnonymized").value(2));

        anonymize(EMAIL, token).andExpect(status().isOk()).andExpect(jsonPath("$.ordersAnonymized").value(0));
        // the placeholder itself is not a customer
        anonymize(CustomerDataService.ERASED_EMAIL, token).andExpect(jsonPath("$.ordersAnonymized").value(0));
        assertThat(orderRepository.count()).isEqualTo(3);
    }

    @Test
    void exportsOnlyThisCustomersOrders() throws Exception {
        mvc.perform(post("/internal/privacy/customers/orders")
                        .header(InternalApiTokenFilter.TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].shippingAddress").value(ADDRESS))
                .andExpect(jsonPath("$[0].items[0].plantName").value("Monstera"))
                .andExpect(jsonPath("$[?(@.shippingAddress == '9 Other Road, Lviv')]").isEmpty());
    }

    @Test
    void rejectsCallsWithoutTheInternalToken() throws Exception {
        anonymize(EMAIL, null).andExpect(status().isUnauthorized());
        anonymize(EMAIL, "wrong-token").andExpect(status().isUnauthorized());

        assertThat(jdbc.queryForObject("select count(*) from orders.orders where customer_email ilike ?", Integer.class, EMAIL))
                .isEqualTo(2);
    }

    private ResultActions anonymize(String email, String internalToken) throws Exception {
        var request = post("/internal/privacy/customers/anonymize")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}");
        if (internalToken != null) {
            request.header(InternalApiTokenFilter.TOKEN_HEADER, internalToken);
        }
        return mvc.perform(request);
    }

    private static Order order(String email, String address, String price) {
        Order order = new Order();
        order.setCustomerEmail(email);
        order.setShippingAddress(address);
        order.setPaymentMethod(PaymentMethod.CARD);
        order.addItem(new OrderItem(1L, "Monstera", 1, new BigDecimal(price)));
        return order;
    }
}
