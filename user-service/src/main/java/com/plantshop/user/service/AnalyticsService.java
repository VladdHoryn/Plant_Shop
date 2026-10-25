package com.plantshop.user.service;

import com.plantshop.user.dto.AnalyticsEventRequest;
import com.plantshop.user.dto.GatedActionResponse;
import com.plantshop.user.entity.AnalyticsEvent;
import com.plantshop.user.entity.ConsentPurpose;
import com.plantshop.user.repository.AnalyticsEventRepository;
import com.plantshop.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Optional product analytics about the signed-in customer: recorded only with OPTIONAL_ANALYTICS consent. */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final UserRepository userRepository;
    private final ConsentPolicyGate gate;
    private final AnalyticsEventRepository analyticsRepository;

    @Transactional
    public GatedActionResponse track(Long userId, AnalyticsEventRequest request) {
        // only for the row lock: a concurrent REVOKE completes before this check or waits for this event
        userRepository.findByIdForUpdate(userId);
        GateDecision decision = gate.check(userId, ConsentPurpose.OPTIONAL_ANALYTICS);
        if (!decision.allowed()) {
            // dropped, not stored anywhere
            return GatedActionResponse.denied(userId, decision);
        }
        AnalyticsEvent event = analyticsRepository.save(new AnalyticsEvent(userId, request.event(), request.page()));
        return GatedActionResponse.allowed(userId, decision, "EVENT_RECORDED", event.getId());
    }
}
