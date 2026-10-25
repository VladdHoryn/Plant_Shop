package com.plantshop.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record MarketingEmailRequest(@NotNull Long userId, @NotNull @Pattern(regexp = "[a-z0-9-]{1,40}") String campaign) {
}
