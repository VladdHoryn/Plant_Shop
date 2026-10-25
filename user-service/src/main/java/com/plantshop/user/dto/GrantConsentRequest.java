package com.plantshop.user.dto;

import com.plantshop.user.entity.ConsentSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** @param policyVersion the version of the consent text the customer was shown and accepted */
public record GrantConsentRequest(@NotBlank String policyVersion, @NotNull ConsentSource source) {
}
