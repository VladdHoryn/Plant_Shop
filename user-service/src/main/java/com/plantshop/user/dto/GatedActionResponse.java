package com.plantshop.user.dto;

import com.plantshop.user.entity.ConsentPurpose;
import com.plantshop.user.service.GateDecision;

/**
 * Outcome of an operation that depends on consent.
 *
 * @param sideEffect what was actually done, NONE when the gate denied the operation
 * @param recordId   id of the created e-mail or analytics record, null when denied
 */
public record GatedActionResponse(Long subjectId, ConsentPurpose purpose, GateDecision.Decision decision,
                                  GateDecision.Reason reason, String sideEffect, Long recordId) {

    public static GatedActionResponse denied(Long subjectId, GateDecision decision) {
        return new GatedActionResponse(subjectId, decision.purpose(), decision.decision(), decision.reason(), "NONE", null);
    }

    public static GatedActionResponse allowed(Long subjectId, GateDecision decision, String sideEffect, Long recordId) {
        return new GatedActionResponse(subjectId, decision.purpose(), decision.decision(), decision.reason(), sideEffect,
                recordId);
    }
}
