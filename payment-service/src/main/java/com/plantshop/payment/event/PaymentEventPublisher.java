package com.plantshop.payment.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Announces payment results only after the payment record is committed. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        log.info("Payment completed for order {}", event.orderId());
        kafkaTemplate.send(KafkaTopics.PAYMENT_COMPLETED, String.valueOf(event.orderId()), event);
    }

    @TransactionalEventListener
    public void onPaymentFailed(PaymentFailedEvent event) {
        log.info("Payment failed for order {}: {}", event.orderId(), event.reason());
        kafkaTemplate.send(KafkaTopics.PAYMENT_FAILED, String.valueOf(event.orderId()), event);
    }
}
