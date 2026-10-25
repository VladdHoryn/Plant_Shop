package com.plantshop.user.service;

/** Where an account action came from; stored in the account history and exported to the customer. */
public record RequestOrigin(String ipAddress, String userAgent) {

    private static final int MAX_USER_AGENT = 255;

    public RequestOrigin {
        if (userAgent != null && userAgent.length() > MAX_USER_AGENT) {
            userAgent = userAgent.substring(0, MAX_USER_AGENT);
        }
    }
}
