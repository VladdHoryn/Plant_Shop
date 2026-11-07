package com.plantshop.user.service.export;

import com.plantshop.user.privacy.OrderHistory;

/** Port: the customer's orders held by order-service. */
public interface OrderHistorySource {

    /** Never throws because the remote source is down: that is reported as {@link OrderHistory#UNAVAILABLE}. */
    OrderHistory ordersOf(String email);
}
