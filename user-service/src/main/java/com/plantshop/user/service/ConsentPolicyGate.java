package com.plantshop.user.service;

import com.plantshop.user.entity.ConsentPurpose;
import com.plantshop.user.entity.ConsentStatus;
import com.plantshop.user.entity.UserConsent;
import com.plantshop.user.entity.UserStatus;
import com.plantshop.user.privacy.PrivacyProperties;
import com.plantshop.user.repository.UserConsentRepository;
import com.plantshop.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Decides whether an optional, consent-based operation may run. It is called immediately before the
 * operation, reads the current state from the database (nothing is cached, so a withdrawal applies to
 * the very next call) and only answers for the purpose asked: consent to analytics never allows
 * marketing. Operations on another legal basis (contract: order and account e-mails) do not use it.
 */
@Component
@RequiredArgsConstructor
public class ConsentPolicyGate {

    private final UserRepository userRepository;
    private final UserConsentRepository consentRepository;
    private final PrivacyProperties properties;

    public GateDecision check(Long userId, ConsentPurpose purpose) {
        if (!userRepository.existsByIdAndStatus(userId, UserStatus.ACTIVE)) {
            return GateDecision.deny(purpose, GateDecision.Reason.SUBJECT_INACTIVE);
        }
        return consentRepository.findByUserIdAndPurpose(userId, purpose)
                .map(consent -> evaluate(consent, purpose))
                .orElse(GateDecision.deny(purpose, GateDecision.Reason.NO_CONSENT));
    }

    private GateDecision evaluate(UserConsent consent, ConsentPurpose purpose) {
        if (consent.getStatus() != ConsentStatus.GRANTED) {
            return GateDecision.deny(purpose, GateDecision.Reason.CONSENT_WITHDRAWN);
        }
        if (!consent.getPolicyVersion().equals(properties.currentPolicyVersion(purpose))) {
            return GateDecision.deny(purpose, GateDecision.Reason.POLICY_VERSION_OUTDATED);
        }
        return GateDecision.allow(purpose);
    }
}
