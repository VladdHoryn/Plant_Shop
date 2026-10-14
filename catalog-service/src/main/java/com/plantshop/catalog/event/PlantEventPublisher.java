package com.plantshop.catalog.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Forwards domain events to Kafka only after the database transaction commits,
 * so other services never see a plant that was rolled back.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlantEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener
    public void onPlantCreated(PlantCreatedEvent event) {
        log.info("Publishing {} for plant {}", KafkaTopics.PLANT_CREATED, event.plantId());
        kafkaTemplate.send(KafkaTopics.PLANT_CREATED, String.valueOf(event.plantId()), event);
    }
}
