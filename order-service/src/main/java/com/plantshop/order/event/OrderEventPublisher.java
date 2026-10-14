package com.plantshop.order.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Starts the saga only once the order row is committed, so consumers can always find it. */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("Publishing {} for order {}", KafkaTopics.ORDER_CREATED, event.orderId());
        kafkaTemplate.send(KafkaTopics.ORDER_CREATED, String.valueOf(event.orderId()), event);
    }
}
