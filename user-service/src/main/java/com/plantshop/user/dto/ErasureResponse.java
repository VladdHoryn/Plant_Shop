package com.plantshop.user.dto;

import com.plantshop.user.privacy.ErasureRule;

import java.time.Instant;
import java.util.List;

/**
 * @param status ANONYMIZED, or ALREADY_ANONYMIZED for a repeated request (nothing was changed)
 * @param steps  one entry per erasure rule; affectedRecords is null for SKIP rules and on a repeat
 */
public record ErasureResponse(Long subjectId, String status, Instant completedAt, String correlationId,
                              List<Step> steps) {

    public record Step(String system, String dataset, ErasureRule.Action action, Integer affectedRecords,
                       String rationale) {

        public static Step of(ErasureRule rule, Integer affectedRecords) {
            return new Step(rule.system(), rule.dataset(), rule.action(), affectedRecords, rule.rationale());
        }
    }
}
