package com.plantshop.user.service.export;

import com.plantshop.user.client.OrderPrivacyClient;
import com.plantshop.user.client.OrderPrivacyClient.CustomerRef;
import com.plantshop.user.mapper.PersonalDataMapper;
import com.plantshop.user.privacy.OrderHistory;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** order-service over Feign. An unavailable source is reported as an omission instead of being silently left out. */
@Slf4j
@Component
public class FeignOrderHistorySource implements OrderHistorySource {

    private final OrderPrivacyClient orderClient;

    public FeignOrderHistorySource(final OrderPrivacyClient orderClient) {
        this.orderClient = orderClient;
    }

    @Override
    public OrderHistory ordersOf(final String email) {
        try {
            return OrderHistory.available(
                    orderClient.findOrders(new CustomerRef(email)).stream().map(PersonalDataMapper::toEntry).toList());
        } catch (FeignException e) {
            log.warn("Orders could not be read for a personal data export: {} status={}",
                    e.getClass().getSimpleName(), e.status());
            return OrderHistory.UNAVAILABLE;
        }
    }
}
