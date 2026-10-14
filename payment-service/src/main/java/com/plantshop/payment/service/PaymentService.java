package com.plantshop.payment.service;

import com.plantshop.payment.dto.PaymentResponse;
import com.plantshop.payment.entity.Payment;
import com.plantshop.payment.entity.PaymentStatus;
import com.plantshop.payment.event.InventoryReservedEvent;
import com.plantshop.payment.event.PaymentCompletedEvent;
import com.plantshop.payment.event.PaymentFailedEvent;
import com.plantshop.payment.exception.ResourceNotFoundException;
import com.plantshop.payment.mapper.PaymentMapper;
import com.plantshop.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentProperties properties;

    /** Charges the order through a simulated provider that declines amounts above the configured limit. */
    @Transactional
    public void process(InventoryReservedEvent event) {
        if (paymentRepository.existsByOrderId(event.orderId())) {
            log.warn("Payment for order {} already processed, ignoring duplicate event", event.orderId());
            return;
        }

        Payment payment = new Payment();
        payment.setOrderId(event.orderId());
        payment.setAmount(event.totalAmount());
        payment.setMethod(event.paymentMethod());

        if (event.totalAmount().compareTo(properties.maxAmount()) > 0) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Amount exceeds the limit of " + properties.maxAmount());
            paymentRepository.save(payment);
            eventPublisher.publishEvent(new PaymentFailedEvent(event.orderId(), payment.getFailureReason()));
            return;
        }

        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setTransactionId(UUID.randomUUID().toString());
        Payment saved = paymentRepository.save(payment);
        eventPublisher.publishEvent(
                new PaymentCompletedEvent(event.orderId(), saved.getId(), saved.getTransactionId()));
    }

    @Transactional(readOnly = true)
    public PaymentResponse findByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .map(PaymentMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("No payment for order " + orderId));
    }

    @Transactional(readOnly = true)
    public PaymentResponse findById(Long id) {
        return paymentRepository.findById(id)
                .map(PaymentMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Payment with id " + id + " not found"));
    }
}
