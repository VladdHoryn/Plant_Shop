package com.plantshop.user.service.export;

import com.plantshop.user.entity.AuditOutcome;
import com.plantshop.user.entity.PrivacyEvent;
import com.plantshop.user.exception.SubjectNotFoundException;
import com.plantshop.user.privacy.ExportRequest;
import com.plantshop.user.privacy.PrivacyAudit;
import com.plantshop.user.security.AppUserPrincipal;
import com.plantshop.user.security.SubjectAccessPolicy;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Function;

/** Access rule of the right of access plus its audit trail. Only the fact of an export is audited, never content. */
@Component
public class AuditedExportAccessGuard implements ExportAccessGuard {

    private final SubjectAccessPolicy accessPolicy;
    private final PrivacyAudit audit;

    public AuditedExportAccessGuard(final SubjectAccessPolicy accessPolicy, final PrivacyAudit audit) {
        this.accessPolicy = accessPolicy;
        this.audit = audit;
    }

    @Override
    public <T> T audited(final AppUserPrincipal actor, final Long subjectId,
                         final Function<ExportRequest, Optional<T>> operation) {
        if (!accessPolicy.canHandleDataRequest(actor, subjectId)) {
            audit.record(PrivacyEvent.PERSONAL_DATA_EXPORT, actor.getId(), subjectId, AuditOutcome.DENIED);
            throw new SubjectNotFoundException();
        }
        final T result = operation.apply(new ExportRequest(actor, subjectId)).orElseThrow(() -> {
            audit.record(PrivacyEvent.PERSONAL_DATA_EXPORT, actor.getId(), subjectId, AuditOutcome.NOT_FOUND);
            return new SubjectNotFoundException();
        });
        audit.record(PrivacyEvent.PERSONAL_DATA_EXPORT, actor.getId(), subjectId, AuditOutcome.SUCCESS);
        return result;
    }
}
