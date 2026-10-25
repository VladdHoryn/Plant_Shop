package com.plantshop.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.plantshop.user.entity.ConsentPurpose;
import com.plantshop.user.entity.ConsentSource;

import java.time.Instant;

/**
 * @param status  GRANTED, WITHDRAWN or NOT_GIVEN
 * @param changed only on GRANT/REVOKE: false when the command was a no-op (idempotent repeat)
 */
public record ConsentResponse(
        ConsentPurpose purpose,
        String status,
        String policyVersion,
        String currentPolicyVersion,
        ConsentSource source,
        Instant grantedAt,
        Instant withdrawnAt,
        Instant updatedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean changed
) {
}
