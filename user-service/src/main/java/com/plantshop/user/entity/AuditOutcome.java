package com.plantshop.user.entity;

public enum AuditOutcome {
    SUCCESS,
    NO_CHANGE,
    DENIED,
    NOT_FOUND,
    FAILED;

    /** The operation did not happen, so its own transaction (if any) will not commit. */
    public boolean isRejection() {
        return this == DENIED || this == NOT_FOUND || this == FAILED;
    }
}
