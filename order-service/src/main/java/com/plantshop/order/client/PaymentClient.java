package com.plantshop.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "payment-service")
public interface PaymentClient {

    /** 404 while the saga has not reached the payment step yet. */
    @GetMapping("/api/payments/order/{orderId}")
    PaymentDto findByOrderId(@PathVariable("orderId") Long orderId);

    record PaymentDto(Long id, Long orderId, String status, String transactionId, String failureReason) {
    }
}
