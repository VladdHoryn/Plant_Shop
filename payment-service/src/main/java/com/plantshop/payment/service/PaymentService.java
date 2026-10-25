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

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    /** Per-method limits of the payment provider. */
    static final BigDecimal WALLET_LIMIT = new BigDecimal("1000");
    static final BigDecimal PAYPAL_LIMIT = new BigDecimal("2500");
    static final BigDecimal CARD_LIMIT = new BigDecimal("5000");

    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentProperties properties;

    /**
     * Charges the order through a simulated provider. Declines unsupported payment methods, amounts above the
     * limit of the payment method, and amounts above the configured global limit.
     */
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

        String method = event.paymentMethod();
        BigDecimal amount = event.totalAmount();
        String declineReason = null;
        if ((method.equals("APPLE_PAY") || method.equals("GOOGLE_PAY")) && amount.compareTo(WALLET_LIMIT) > 0) {
            declineReason = "Wallet payments are limited to " + WALLET_LIMIT;
        } else if (method.equals("PAYPAL") && amount.compareTo(PAYPAL_LIMIT) > 0) {
            declineReason = "PayPal payments are limited to " + PAYPAL_LIMIT;
        } else if (method.equals("CARD") && amount.compareTo(CARD_LIMIT) > 0) {
            declineReason = "Card payments are limited to " + CARD_LIMIT;
        } else if (!method.equals("CARD") && !method.equals("PAYPAL")
                && !method.equals("APPLE_PAY") && !method.equals("GOOGLE_PAY")) {
            declineReason = "Unsupported payment method " + method;
        }

        if (declineReason != null || amount.compareTo(properties.maxAmount()) > 0) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(declineReason != null
                    ? declineReason
                    : "Amount exceeds the limit of " + properties.maxAmount());
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
