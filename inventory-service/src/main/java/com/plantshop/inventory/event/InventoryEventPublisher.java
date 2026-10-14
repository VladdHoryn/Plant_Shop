package com.plantshop.inventory.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Forwards domain events to Kafka only after the stock changes are committed. */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener
    public void onInventoryReserved(InventoryReservedEvent event) {
        log.info("Stock reserved for order {}", event.orderId());
        kafkaTemplate.send(KafkaTopics.INVENTORY_RESERVED, String.valueOf(event.orderId()), event);
    }

    @TransactionalEventListener
    public void onInventoryFailed(InventoryFailedEvent event) {
        log.info("Stock reservation failed for order {}: {}", event.orderId(), event.reason());
        kafkaTemplate.send(KafkaTopics.INVENTORY_FAILED, String.valueOf(event.orderId()), event);
    }
}
