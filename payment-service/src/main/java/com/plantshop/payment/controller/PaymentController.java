package com.plantshop.payment.controller;

import com.plantshop.payment.dto.PaymentResponse;
import com.plantshop.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/{id}")
    public PaymentResponse findById(@PathVariable Long id) {
        return paymentService.findById(id);
    }

    @GetMapping("/order/{orderId}")
    public PaymentResponse findByOrderId(@PathVariable Long orderId) {
        return paymentService.findByOrderId(orderId);
    }
}
