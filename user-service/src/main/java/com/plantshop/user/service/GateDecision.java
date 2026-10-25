package com.plantshop.user.service;

import com.plantshop.user.entity.ConsentPurpose;

public record GateDecision(ConsentPurpose purpose, Decision decision, Reason reason) {

    public enum Decision {
        ALLOW,
        DENY
    }

    public enum Reason {
        CONSENT_GRANTED,
        NO_CONSENT,
        CONSENT_WITHDRAWN,
        /** Granted for an older consent text; the customer has to accept the current one. */
        POLICY_VERSION_OUTDATED,
        SUBJECT_INACTIVE
    }

    static GateDecision allow(ConsentPurpose purpose) {
        return new GateDecision(purpose, Decision.ALLOW, Reason.CONSENT_GRANTED);
    }

    static GateDecision deny(ConsentPurpose purpose, Reason reason) {
        return new GateDecision(purpose, Decision.DENY, reason);
    }

    public boolean allowed() {
        return decision == Decision.ALLOW;
    }
}
