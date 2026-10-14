package com.plantshop.payment.service;

import com.plantshop.payment.entity.Payment;
import com.plantshop.payment.entity.PaymentStatus;
import com.plantshop.payment.event.InventoryReservedEvent;
import com.plantshop.payment.event.PaymentCompletedEvent;
import com.plantshop.payment.event.PaymentFailedEvent;
import com.plantshop.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, eventPublisher, new PaymentProperties(new BigDecimal("100")));
    }

    @Test
    void completesPaymentWithinLimit() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentService.process(new InventoryReservedEvent(1L, "a@b.com", new BigDecimal("99.99"), "CARD"));

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(saved.getValue().getTransactionId()).isNotBlank();
        verify(eventPublisher).publishEvent(any(PaymentCompletedEvent.class));
    }

    @Test
    void declinesPaymentAboveLimit() {
        paymentService.process(new InventoryReservedEvent(1L, "a@b.com", new BigDecimal("100.01"), "CARD"));

        verify(eventPublisher).publishEvent(any(PaymentFailedEvent.class));
        verify(eventPublisher, never()).publishEvent(any(PaymentCompletedEvent.class));
    }

    @Test
    void ignoresDuplicateEvent() {
        when(paymentRepository.existsByOrderId(1L)).thenReturn(true);

        paymentService.process(new InventoryReservedEvent(1L, "a@b.com", BigDecimal.TEN, "CARD"));

        verify(paymentRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
