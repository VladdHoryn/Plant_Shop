package com.plantshop.payment.service;

import com.plantshop.payment.entity.Payment;
import com.plantshop.payment.entity.PaymentStatus;
import com.plantshop.payment.event.InventoryReservedEvent;
import com.plantshop.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Business rules per payment method. The table is the behaviour contract: it must hold for the if/else
 * implementation and for the lookup-table implementation alike.
 */
@ExtendWith(MockitoExtension.class)
class PaymentMethodRulesTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, eventPublisher,
                new PaymentProperties(new BigDecimal("10000")));
    }

    @ParameterizedTest(name = "{0} {1} -> {2}")
    @CsvSource(delimiter = '|', textBlock = """
            CARD       | 5000.00  | COMPLETED |
            CARD       | 5000.01  | FAILED    | Card payments are limited to 5000
            PAYPAL     | 2500.00  | COMPLETED |
            PAYPAL     | 2500.01  | FAILED    | PayPal payments are limited to 2500
            APPLE_PAY  | 1000.00  | COMPLETED |
            APPLE_PAY  | 1000.01  | FAILED    | Wallet payments are limited to 1000
            GOOGLE_PAY | 999.99   | COMPLETED |
            GOOGLE_PAY | 1500.00  | FAILED    | Wallet payments are limited to 1000
            CRYPTO     | 1.00     | FAILED    | Unsupported payment method CRYPTO
            """)
    void appliesTheRuleOfThePaymentMethod(String method, String amount, PaymentStatus status, String reason) {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentService.process(new InventoryReservedEvent(1L, new BigDecimal(amount), method));

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(status);
        assertThat(saved.getValue().getFailureReason()).isEqualTo(reason);
    }

    @ParameterizedTest(name = "global limit applies to {0}")
    @CsvSource({"CARD", "PAYPAL", "APPLE_PAY"})
    void globalLimitStillAppliesBelowTheMethodLimit(String method) {
        paymentService = new PaymentService(paymentRepository, eventPublisher, new PaymentProperties(new BigDecimal("100")));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentService.process(new InventoryReservedEvent(1L, new BigDecimal("100.01"), method));

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(saved.getValue().getFailureReason()).isEqualTo("Amount exceeds the limit of 100");
    }
}
