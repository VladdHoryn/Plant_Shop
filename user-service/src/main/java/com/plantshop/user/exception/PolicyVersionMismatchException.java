package com.plantshop.user.exception;

import com.plantshop.user.entity.ConsentPurpose;
import lombok.Getter;

/** The customer accepted a consent text that is not the current one for this purpose. */
@Getter
public class PolicyVersionMismatchException extends RuntimeException {

    private final String currentVersion;

    public PolicyVersionMismatchException(ConsentPurpose purpose, String currentVersion) {
        super("Consent for " + purpose + " must be given for the current policy version");
        this.currentVersion = currentVersion;
    }
}
