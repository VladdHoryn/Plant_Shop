package com.plantshop.user.dto;

import java.util.List;

public record ConsentOverview(Long subjectId, List<ConsentResponse> consents, List<ConsentHistoryResponse> history) {
}
