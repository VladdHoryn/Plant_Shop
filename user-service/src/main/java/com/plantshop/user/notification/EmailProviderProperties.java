package com.plantshop.user.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("email.provider")
public record EmailProviderProperties(String url, String apiKey) {
}
