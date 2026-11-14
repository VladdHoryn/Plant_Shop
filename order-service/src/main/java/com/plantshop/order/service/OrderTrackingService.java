package com.plantshop.order.service;

import com.plantshop.order.client.PaymentClient;
import com.plantshop.order.client.PaymentClient.PaymentDto;
import com.plantshop.order.dto.OrderResponse;
import com.plantshop.order.dto.OrderTrackingResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Tracking view of an order: the order from this service plus its payment, read synchronously from
 * payment-service. A missing payment (404) means the saga has not reached the payment step yet.
 */
@Service
@RequiredArgsConstructor
public class OrderTrackingService {

    private final OrderService orderService;
    private final PaymentClient paymentClient;

    public OrderTrackingResponse track(Long orderId) {
        OrderResponse order = orderService.findById(orderId);
        PaymentDto payment = findPayment(orderId);
        String paymentStatus = payment == null ? OrderTrackingResponse.PAYMENT_NOT_STARTED : payment.status();
        String transactionId = payment == null ? null : payment.transactionId();
        return new OrderTrackingResponse(order.id(), order.status(), paymentStatus, transactionId,
                order.failureReason(), nextStep(order));
    }

    private PaymentDto findPayment(Long orderId) {
        try {
            return paymentClient.findByOrderId(orderId);
        } catch (FeignException.NotFound e) {
            return null;
        }
    }

    private static String nextStep(OrderResponse order) {
        return switch (order.status()) {
            case PENDING -> "Waiting for stock reservation and payment";
            case CONFIRMED -> "Paid, being prepared for shipment";
            case CANCELLED -> "Cancelled, nothing will be charged or shipped";
        };
    }
}
