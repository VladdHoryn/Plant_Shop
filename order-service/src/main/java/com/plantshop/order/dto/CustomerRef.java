package com.plantshop.order.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Identifies a customer's orders. Sent in a POST body so the e-mail never appears in a URL. */
public record CustomerRef(@NotBlank @Email String email) {
}
