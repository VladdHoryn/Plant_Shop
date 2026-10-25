package com.plantshop.user.dto;

import com.plantshop.user.entity.ConsentSource;
import jakarta.validation.constraints.NotNull;

public record RevokeConsentRequest(@NotNull ConsentSource source) {
}
