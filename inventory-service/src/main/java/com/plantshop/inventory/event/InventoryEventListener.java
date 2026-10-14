package com.plantshop.inventory.event;

import com.plantshop.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryEventListener {

    private final InventoryService inventoryService;

    @KafkaListener(topics = KafkaTopics.PLANT_CREATED)
    public void onPlantCreated(PlantCreatedEvent event) {
        inventoryService.createIfAbsent(event.plantId());
    }

    @KafkaListener(topics = KafkaTopics.ORDER_CREATED)
    public void onOrderCreated(OrderCreatedEvent event) {
        inventoryService.reserve(event);
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_COMPLETED)
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        inventoryService.commitReservations(event.orderId());
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_FAILED)
    public void onPaymentFailed(PaymentFailedEvent event) {
        inventoryService.releaseReservations(event.orderId());
    }
}
