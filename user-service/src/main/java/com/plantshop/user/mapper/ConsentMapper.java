package com.plantshop.user.mapper;

import com.plantshop.user.dto.ConsentHistoryResponse;
import com.plantshop.user.dto.ConsentResponse;
import com.plantshop.user.entity.ConsentHistoryEntry;
import com.plantshop.user.entity.ConsentPurpose;
import com.plantshop.user.entity.UserConsent;

public final class ConsentMapper {

    private ConsentMapper() {
    }

    /** @param consent null when the customer has never decided on this purpose */
    public static ConsentResponse toResponse(ConsentPurpose purpose, UserConsent consent, String currentPolicyVersion,
                                             Boolean changed) {
        if (consent == null) {
            return new ConsentResponse(purpose, "NOT_GIVEN", null, currentPolicyVersion, null, null, null, null, changed);
        }
        return new ConsentResponse(purpose, consent.getStatus().name(), consent.getPolicyVersion(), currentPolicyVersion,
                consent.getSource(), consent.getGrantedAt(), consent.getWithdrawnAt(), consent.getUpdatedAt(), changed);
    }

    public static ConsentHistoryResponse toResponse(ConsentHistoryEntry entry) {
        return new ConsentHistoryResponse(entry.getPurpose(), entry.getAction(), entry.getPolicyVersion(),
                entry.getSource(), entry.getOccurredAt());
    }
}
