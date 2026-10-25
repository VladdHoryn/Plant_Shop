package com.plantshop.order.controller;

import com.plantshop.order.dto.AnonymizationResult;
import com.plantshop.order.dto.CustomerOrderExport;
import com.plantshop.order.dto.CustomerRef;
import com.plantshop.order.service.CustomerDataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Called by user-service only (token-protected, not routed by the gateway); user-service performs the
 * authorization of the data subject or privacy officer.
 */
@RestController
@RequestMapping("/internal/privacy/customers")
@RequiredArgsConstructor
public class InternalPrivacyController {

    private final CustomerDataService customerDataService;

    @PostMapping("/orders")
    public List<CustomerOrderExport> findOrders(@Valid @RequestBody CustomerRef customer) {
        return customerDataService.findOrders(customer.email());
    }

    @PostMapping("/anonymize")
    public AnonymizationResult anonymize(@Valid @RequestBody CustomerRef customer) {
        return new AnonymizationResult(customerDataService.anonymizeOrders(customer.email()));
    }
}
