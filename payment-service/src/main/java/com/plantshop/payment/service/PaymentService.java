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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    /** A limit and the text used when an amount is above it. */
    record LimitRule(BigDecimal limit, String declineReason) {

        boolean declines(BigDecimal amount) {
            return amount.compareTo(limit) > 0;
        }
    }

    private static final BigDecimal WALLET_LIMIT = new BigDecimal("1000");
    /** Below any amount: a payment method without a rule is always declined. */
    private static final BigDecimal DECLINE_ALL = BigDecimal.ONE.negate();

    /** The business rules of the payment provider as data: one entry per supported payment method. */
    static final Map<String, LimitRule> METHOD_RULES = Map.of(
            "CARD", new LimitRule(new BigDecimal("5000"), "Card payments are limited to 5000"),
            "PAYPAL", new LimitRule(new BigDecimal("2500"), "PayPal payments are limited to 2500"),
            "APPLE_PAY", new LimitRule(WALLET_LIMIT, "Wallet payments are limited to 1000"),
            "GOOGLE_PAY", new LimitRule(WALLET_LIMIT, "Wallet payments are limited to 1000"));

    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentProperties properties;

    /**
     * Charges the order through a simulated provider. The rule of the payment method is looked up in
     * {@link #METHOD_RULES} and checked first, then the configured global limit; the first rule the amount
     * breaks declines the payment.
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

        LimitRule methodRule = METHOD_RULES.getOrDefault(event.paymentMethod(),
                new LimitRule(DECLINE_ALL, "Unsupported payment method " + event.paymentMethod()));
        LimitRule globalRule = new LimitRule(properties.maxAmount(), "Amount exceeds the limit of " + properties.maxAmount());
        Stream.of(methodRule, globalRule)
                .filter(rule -> rule.declines(event.totalAmount()))
                .map(LimitRule::declineReason)
                .findFirst()
                .ifPresentOrElse(reason -> decline(payment, reason), () -> complete(payment));
    }

    private void decline(Payment payment, String reason) {
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(reason);
        paymentRepository.save(payment);
        eventPublisher.publishEvent(new PaymentFailedEvent(payment.getOrderId(), reason));
    }

    private void complete(Payment payment) {
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setTransactionId(UUID.randomUUID().toString());
        Payment saved = paymentRepository.save(payment);
        eventPublisher.publishEvent(
                new PaymentCompletedEvent(payment.getOrderId(), saved.getId(), saved.getTransactionId()));
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
