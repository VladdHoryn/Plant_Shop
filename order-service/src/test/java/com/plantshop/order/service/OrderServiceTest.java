package com.plantshop.order.service;

import com.plantshop.order.client.CatalogClient;
import com.plantshop.order.client.CatalogClient.PlantDto;
import com.plantshop.order.client.InventoryClient;
import com.plantshop.order.client.InventoryClient.StockCheckResponse;
import com.plantshop.order.dto.CreateOrderRequest;
import com.plantshop.order.dto.OrderItemRequest;
import com.plantshop.order.dto.OrderResponse;
import com.plantshop.order.entity.Order;
import com.plantshop.order.entity.OrderStatus;
import com.plantshop.order.entity.PaymentMethod;
import com.plantshop.order.event.OrderCreatedEvent;
import com.plantshop.order.exception.InvalidOrderException;
import com.plantshop.order.exception.OutOfStockException;
import com.plantshop.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private CatalogClient catalogClient;
    @Mock
    private InventoryClient inventoryClient;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private TransactionTemplate transactionTemplate;
    @InjectMocks
    private OrderService orderService;

    private final CreateOrderRequest request = new CreateOrderRequest("a@b.com", "1 Green St", PaymentMethod.CARD,
            List.of(new OrderItemRequest(1L, 2), new OrderItemRequest(1L, 1)));

    @Test
    void createMergesLinesComputesTotalAndPublishesEvent() {
        when(catalogClient.findByIds(anyCollection()))
                .thenReturn(List.of(new PlantDto(1L, "Monstera", new BigDecimal("10.00"), true)));
        when(inventoryClient.check(anyList())).thenReturn(List.of(new StockCheckResponse(1L, 3, 10, true)));
        when(transactionTemplate.execute(any())).thenAnswer(inv ->
                inv.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order order = inv.getArgument(0);
            order.setId(5L);
            return order;
        });

        OrderResponse response = orderService.create(request);

        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().quantity()).isEqualTo(3);
        assertThat(response.totalAmount()).isEqualByComparingTo("30.00");
        verify(eventPublisher).publishEvent(any(OrderCreatedEvent.class));
    }

    @Test
    void createRejectsInactivePlant() {
        when(catalogClient.findByIds(anyCollection()))
                .thenReturn(List.of(new PlantDto(1L, "Monstera", new BigDecimal("10.00"), false)));

        assertThatThrownBy(() -> orderService.create(request)).isInstanceOf(InvalidOrderException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createRejectsWhenOutOfStock() {
        when(catalogClient.findByIds(anyCollection()))
                .thenReturn(List.of(new PlantDto(1L, "Monstera", new BigDecimal("10.00"), true)));
        when(inventoryClient.check(anyList())).thenReturn(List.of(new StockCheckResponse(1L, 3, 1, false)));

        assertThatThrownBy(() -> orderService.create(request)).isInstanceOf(OutOfStockException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void cancelIgnoresAlreadyConfirmedOrder() {
        Order order = new Order();
        order.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        orderService.cancel(5L, "late failure");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getFailureReason()).isNull();
    }
}
