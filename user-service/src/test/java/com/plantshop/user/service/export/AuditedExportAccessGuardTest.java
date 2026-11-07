package com.plantshop.user.service.export;

import com.plantshop.user.entity.AuditOutcome;
import com.plantshop.user.entity.PrivacyEvent;
import com.plantshop.user.entity.User;
import com.plantshop.user.entity.UserRole;
import com.plantshop.user.exception.SubjectNotFoundException;
import com.plantshop.user.privacy.PrivacyAudit;
import com.plantshop.user.security.AppUserPrincipal;
import com.plantshop.user.security.SubjectAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** The audit outcome of every export attempt: DENIED, NOT_FOUND or SUCCESS. */
@ExtendWith(MockitoExtension.class)
class AuditedExportAccessGuardTest {

    private static final long ACTOR_ID = 7L;
    private static final long SUBJECT_ID = 42L;

    @Mock
    private SubjectAccessPolicy accessPolicy;
    @Mock
    private PrivacyAudit audit;
    private AuditedExportAccessGuard guard;
    private AppUserPrincipal actor;

    @BeforeEach
    void setUp() {
        guard = new AuditedExportAccessGuard(accessPolicy, audit);
        final User user = new User();
        user.setId(ACTOR_ID);
        user.setRole(UserRole.CUSTOMER);
        actor = new AppUserPrincipal(user);
    }

    @Test
    void aDeniedRequestIsAuditedAndTheOperationNeverRuns() {
        final AtomicBoolean ran = new AtomicBoolean();
        when(accessPolicy.canHandleDataRequest(actor, SUBJECT_ID)).thenReturn(false);

        assertThatThrownBy(() -> guard.audited(actor, SUBJECT_ID, request -> {
            ran.set(true);
            return Optional.of("data");
        })).isInstanceOf(SubjectNotFoundException.class);

        assertThat(ran).isFalse();
        verify(audit).record(PrivacyEvent.PERSONAL_DATA_EXPORT, ACTOR_ID, SUBJECT_ID, AuditOutcome.DENIED);
        verifyNoMoreInteractions(audit);
    }

    @Test
    void anEmptyResultIsAuditedAsNotFound() {
        when(accessPolicy.canHandleDataRequest(actor, SUBJECT_ID)).thenReturn(true);

        assertThatThrownBy(() -> guard.audited(actor, SUBJECT_ID, request -> Optional.empty()))
                .isInstanceOf(SubjectNotFoundException.class);

        verify(audit).record(PrivacyEvent.PERSONAL_DATA_EXPORT, ACTOR_ID, SUBJECT_ID, AuditOutcome.NOT_FOUND);
        verifyNoMoreInteractions(audit);
    }

    @Test
    void aResultIsReturnedAuditedAsSuccessWithTheCheckedRequest() {
        when(accessPolicy.canHandleDataRequest(actor, SUBJECT_ID)).thenReturn(true);

        final String result = guard.audited(actor, SUBJECT_ID,
                request -> Optional.of(request.subjectId() + ":" + request.requestedBy()));

        assertThat(result).isEqualTo("42:PRIVACY_OFFICER");
        verify(audit).record(PrivacyEvent.PERSONAL_DATA_EXPORT, ACTOR_ID, SUBJECT_ID, AuditOutcome.SUCCESS);
        verifyNoMoreInteractions(audit);
    }
}
