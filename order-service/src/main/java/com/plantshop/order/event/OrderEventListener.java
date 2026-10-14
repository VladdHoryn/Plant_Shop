package com.plantshop.order.event;

import com.plantshop.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final OrderService orderService;

    @KafkaListener(topics = KafkaTopics.PAYMENT_COMPLETED)
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        orderService.confirm(event.orderId());
    }

    @KafkaListener(topics = KafkaTopics.INVENTORY_FAILED)
    public void onInventoryFailed(InventoryFailedEvent event) {
        orderService.cancel(event.orderId(), event.reason());
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_FAILED)
    public void onPaymentFailed(PaymentFailedEvent event) {
        orderService.cancel(event.orderId(), event.reason());
    }
}
