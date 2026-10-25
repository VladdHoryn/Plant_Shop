package com.plantshop.user.exception;

/** A service holding part of the user's data did not answer; the operation can be repeated safely. */
public class SourceUnavailableException extends RuntimeException {

    public SourceUnavailableException(String service, Throwable cause) {
        super(service + " is unavailable, nothing was changed; please repeat the request later", cause);
    }
}
