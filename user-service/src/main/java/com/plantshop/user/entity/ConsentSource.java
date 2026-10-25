package com.plantshop.user.entity;

/** The channel through which the consent changed. SYSTEM is reserved for the erasure workflow. */
public enum ConsentSource {
    WEB,
    MOBILE_APP,
    EMAIL_LINK,
    SUPPORT,
    SYSTEM
}
