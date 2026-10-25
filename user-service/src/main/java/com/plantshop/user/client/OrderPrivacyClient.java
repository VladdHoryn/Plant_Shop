package com.plantshop.user.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * order-service holds the customer's orders, linked by e-mail. The e-mail travels in a POST body,
 * never in a URL, so it cannot end up in access logs or proxies.
 */
@FeignClient(name = "order-service", path = "/internal/privacy/customers")
public interface OrderPrivacyClient {

    @PostMapping("/orders")
    List<CustomerOrder> findOrders(@RequestBody CustomerRef customer);

    /** Idempotent: a second call finds no orders under the e-mail and returns 0. */
    @PostMapping("/anonymize")
    OrdersAnonymized anonymizeOrders(@RequestBody CustomerRef customer);

    record CustomerRef(String email) {
    }

    record CustomerOrder(Long orderId, String status, String paymentMethod, BigDecimal totalAmount,
                         String shippingAddress, Instant createdAt, List<Item> items) {

        public record Item(Long plantId, String plantName, int quantity, BigDecimal unitPrice) {
        }
    }

    record OrdersAnonymized(int ordersAnonymized) {
    }
}
