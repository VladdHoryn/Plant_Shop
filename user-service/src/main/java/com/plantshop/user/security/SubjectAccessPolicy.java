package com.plantshop.user.security;

import com.plantshop.user.entity.UserRole;
import org.springframework.stereotype.Component;

/**
 * Who may act on whose personal data. The subject id from the URL is only compared with the
 * authenticated principal, it never grants access by itself.
 */
@Component
public class SubjectAccessPolicy {

    /** Export and erasure: the data subject, or a privacy officer handling a data-subject request. */
    public boolean canHandleDataRequest(AppUserPrincipal actor, Long subjectId) {
        return actor.isSubject(subjectId) || actor.getRole() == UserRole.PRIVACY_OFFICER;
    }

    /** Consent is personal: nobody can give or withdraw it on the subject's behalf. */
    public boolean canManageConsents(AppUserPrincipal actor, Long subjectId) {
        return actor.isSubject(subjectId);
    }
}
