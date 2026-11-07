package com.plantshop.user.service.export;

import com.plantshop.user.privacy.ExportRequest;
import com.plantshop.user.security.AppUserPrincipal;

import java.util.Optional;
import java.util.function.Function;

/** Port: who may export whose data, and the security audit of every attempt. */
public interface ExportAccessGuard {

    /**
     * Runs {@code operation} only if {@code actor} may handle a data request of {@code subjectId} and audits the
     * outcome: DENIED (operation not run), NOT_FOUND (operation returned empty) or SUCCESS. The operation receives
     * the checked {@link ExportRequest}. Another customer's id and an unknown id end in the same exception, so the
     * caller cannot tell whether that profile exists.
     */
    <T> T audited(AppUserPrincipal actor, Long subjectId, Function<ExportRequest, Optional<T>> operation);
}
