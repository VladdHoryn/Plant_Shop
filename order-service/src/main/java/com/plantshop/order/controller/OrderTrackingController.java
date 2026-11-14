package com.plantshop.order.controller;

import com.plantshop.order.dto.OrderTrackingResponse;
import com.plantshop.order.service.OrderTrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderTrackingController {

    private final OrderTrackingService trackingService;

    /** Order status and payment status in one response; 404 if the order does not exist. */
    @GetMapping("/{id}/tracking")
    public OrderTrackingResponse track(@PathVariable Long id) {
        return trackingService.track(id);
    }
}
