package com.plantshop.order.service;

import com.plantshop.order.client.PaymentClient;
import com.plantshop.order.client.PaymentClient.PaymentDto;
import com.plantshop.order.dto.OrderResponse;
import com.plantshop.order.dto.OrderTrackingResponse;
import com.plantshop.order.entity.OrderStatus;
import com.plantshop.order.entity.PaymentMethod;
import com.plantshop.order.exception.ResourceNotFoundException;
import feign.FeignException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderTrackingServiceTest {

    @Mock
    private OrderService orderService;

    @Mock
    private PaymentClient paymentClient;

    @InjectMocks
    private OrderTrackingService trackingService;

    private static OrderResponse order(OrderStatus status, String failureReason) {
        return new OrderResponse(7L, "jane@example.com", "12 Fern Street", PaymentMethod.CARD, status,
                new BigDecimal("40.00"), failureReason, List.of(), Instant.parse("2026-11-14T10:00:00Z"));
    }

    @Test
    void confirmedOrderShowsItsCompletedPayment() {
        when(orderService.findById(7L)).thenReturn(order(OrderStatus.CONFIRMED, null));
        when(paymentClient.findByOrderId(7L)).thenReturn(new PaymentDto(3L, 7L, "COMPLETED", "tx-42", null));

        OrderTrackingResponse tracking = trackingService.track(7L);

        assertThat(tracking.orderStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(tracking.paymentStatus()).isEqualTo("COMPLETED");
        assertThat(tracking.transactionId()).isEqualTo("tx-42");
        assertThat(tracking.nextStep()).isEqualTo("Paid, being prepared for shipment");
    }

    @Test
    void pendingOrderWithoutPaymentYetIsNotStarted() {
        when(orderService.findById(7L)).thenReturn(order(OrderStatus.PENDING, null));
        when(paymentClient.findByOrderId(7L)).thenThrow(mock(FeignException.NotFound.class));

        OrderTrackingResponse tracking = trackingService.track(7L);

        assertThat(tracking.paymentStatus()).isEqualTo(OrderTrackingResponse.PAYMENT_NOT_STARTED);
        assertThat(tracking.transactionId()).isNull();
        assertThat(tracking.nextStep()).isEqualTo("Waiting for stock reservation and payment");
    }

    @Test
    void cancelledOrderKeepsTheFailureReason() {
        when(orderService.findById(7L)).thenReturn(order(OrderStatus.CANCELLED, "Amount above the limit"));
        when(paymentClient.findByOrderId(7L))
                .thenReturn(new PaymentDto(3L, 7L, "FAILED", null, "Amount above the limit"));

        OrderTrackingResponse tracking = trackingService.track(7L);

        assertThat(tracking.paymentStatus()).isEqualTo("FAILED");
        assertThat(tracking.failureReason()).isEqualTo("Amount above the limit");
        assertThat(tracking.nextStep()).isEqualTo("Cancelled, nothing will be charged or shipped");
    }

    @Test
    void unknownOrderIsNotFoundAndPaymentServiceIsNotCalled() {
        when(orderService.findById(99L)).thenThrow(new ResourceNotFoundException("Order", 99L));

        assertThatThrownBy(() -> trackingService.track(99L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(paymentClient);
    }
}
