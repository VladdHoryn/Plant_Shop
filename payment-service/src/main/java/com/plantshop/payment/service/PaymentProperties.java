package com.plantshop.payment.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;

/**
 * @param maxAmount the simulated provider declines any payment above this amount
 */
@ConfigurationProperties("payment")
public record PaymentProperties(@DefaultValue("10000") BigDecimal maxAmount) {
}
