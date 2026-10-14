package com.plantshop.order.dto;

import com.plantshop.order.entity.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(
        @NotBlank @Email String customerEmail,
        @NotBlank @Size(max = 500) String shippingAddress,
        @NotNull PaymentMethod paymentMethod,
        @NotEmpty List<@Valid OrderItemRequest> items
) {
}
