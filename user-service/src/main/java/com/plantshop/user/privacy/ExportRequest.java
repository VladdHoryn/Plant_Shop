package com.plantshop.user.privacy;

import com.plantshop.user.security.AppUserPrincipal;

import java.util.Objects;

/** Who asked for an export of whose data. Access has already been checked when this is created. */
public record ExportRequest(AppUserPrincipal actor, Long subjectId) {

    public ExportRequest {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(subjectId, "subjectId");
    }

    /** The officer's identity is not part of the export, only the fact that an officer asked. */
    public String requestedBy() {
        return actor.isSubject(subjectId) ? "SUBJECT" : "PRIVACY_OFFICER";
    }
}
