package com.plantshop.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AnalyticsEventRequest(@NotNull @Pattern(regexp = "[a-z_]{1,60}") String event, @Size(max = 200) String page) {
}
