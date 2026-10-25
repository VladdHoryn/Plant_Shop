package com.plantshop.user.dto;

import com.plantshop.user.entity.ConsentAction;
import com.plantshop.user.entity.ConsentPurpose;
import com.plantshop.user.entity.ConsentSource;

import java.time.Instant;

public record ConsentHistoryResponse(ConsentPurpose purpose, ConsentAction action, String policyVersion,
                                     ConsentSource source, Instant occurredAt) {
}
