package com.plantshop.user.service.export;

import com.plantshop.user.client.OrderPrivacyClient;
import com.plantshop.user.client.OrderPrivacyClient.CustomerOrder;
import com.plantshop.user.client.OrderPrivacyClient.CustomerRef;
import com.plantshop.user.privacy.OrderHistory;
import feign.FeignException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** The remote order history: available with entries, or UNAVAILABLE when order-service does not answer. */
@ExtendWith(MockitoExtension.class)
class FeignOrderHistorySourceTest {

    private static final CustomerRef CUSTOMER = new CustomerRef("subject@example.test");

    @Mock
    private OrderPrivacyClient orderClient;

    @Test
    void ordersAreMappedToExportEntries() {
        when(orderClient.findOrders(CUSTOMER)).thenReturn(List.of(new CustomerOrder(5L, "CONFIRMED", "CARD",
                new BigDecimal("12.50"), "Test street 1", Instant.parse("2026-10-01T10:00:00Z"), List.of())));

        final OrderHistory history = new FeignOrderHistorySource(orderClient).ordersOf(CUSTOMER.email());

        assertThat(history.sourceAvailable()).isTrue();
        assertThat(history.entries()).hasSize(1);
    }

    @Test
    void aFailingOrderServiceIsReportedNotThrown() {
        when(orderClient.findOrders(CUSTOMER)).thenThrow(FeignException.class);

        assertThat(new FeignOrderHistorySource(orderClient).ordersOf(CUSTOMER.email()))
                .isSameAs(OrderHistory.UNAVAILABLE);
    }
}
