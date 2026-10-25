package com.plantshop.order.service;

import com.plantshop.order.dto.CustomerOrderExport;
import com.plantshop.order.mapper.OrderMapper;
import com.plantshop.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** The order part of the data-subject requests handled by user-service (export and erasure). */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerDataService {

    /**
     * Neutral placeholders written over the customer data of an erased customer. The orders themselves are
     * retained (accounting records), so amounts, items, status and dates stay and reports keep working.
     * Every erased customer gets the same value, so retained orders cannot be grouped back per person.
     */
    public static final String ERASED_EMAIL = "erased-customer@anonymized.invalid";
    public static final String ERASED_ADDRESS = "[erased]";

    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public List<CustomerOrderExport> findOrders(String email) {
        return orderRepository.findByCustomerEmailIgnoreCaseOrderByCreatedAtDesc(email).stream()
                .map(OrderMapper::toExport)
                .toList();
    }

    /** Idempotent: once anonymized, no order carries the e-mail any more and a repeat returns 0. */
    @Transactional
    public int anonymizeOrders(String email) {
        if (ERASED_EMAIL.equalsIgnoreCase(email)) {
            return 0;
        }
        int anonymized = orderRepository.anonymizeCustomer(email, ERASED_EMAIL, ERASED_ADDRESS, Instant.now());
        log.info("Customer data removed from {} orders", anonymized);
        return anonymized;
    }
}
