package com.plantshop.payment.event;

import com.plantshop.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final PaymentService paymentService;

    @KafkaListener(topics = KafkaTopics.INVENTORY_RESERVED)
    public void onInventoryReserved(InventoryReservedEvent event) {
        paymentService.process(event);
    }
}
