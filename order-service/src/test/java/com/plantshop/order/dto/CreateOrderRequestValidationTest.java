package com.plantshop.order.dto;

import com.plantshop.order.entity.PaymentMethod;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Contract drift found by Schemathesis (Lab 11): "items": [null] gave 500 instead of a documented 400. */
class CreateOrderRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void nullItemIsAValidationError() {
        CreateOrderRequest request = request(Arrays.asList((OrderItemRequest) null));

        assertThat(validator.validate(request))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("items[0].<list element>");
    }

    @Test
    void requestFromTheContractExampleIsValid() {
        CreateOrderRequest request = request(List.of(new OrderItemRequest(1L, 2), new OrderItemRequest(3L, 1)));

        assertThat(validator.validate(request)).isEmpty();
    }

    private static CreateOrderRequest request(List<OrderItemRequest> items) {
        return new CreateOrderRequest("jane@example.com", "12 Fern Street, Kyiv", PaymentMethod.CARD, items);
    }
}
