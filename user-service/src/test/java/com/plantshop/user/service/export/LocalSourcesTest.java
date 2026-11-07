package com.plantshop.user.service.export;

import com.plantshop.user.entity.User;
import com.plantshop.user.privacy.PrivacyAudit;
import com.plantshop.user.repository.AddressRepository;
import com.plantshop.user.repository.AnalyticsEventRepository;
import com.plantshop.user.repository.ConsentHistoryRepository;
import com.plantshop.user.repository.EmailMessageRepository;
import com.plantshop.user.repository.PrivacyAuditEventRepository;
import com.plantshop.user.repository.UserActivityRepository;
import com.plantshop.user.repository.UserConsentRepository;
import com.plantshop.user.repository.UserRepository;
import com.plantshop.user.repository.UserSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The three local sources of the snapshot. The snapshot source is tested with its two collaborator ports
 * replaced by lambdas (they are functional interfaces), the readers against mocked repositories.
 */
@ExtendWith(MockitoExtension.class)
class LocalSourcesTest {

    private static final long SUBJECT_ID = 42L;
    private static final CustomerChoices NO_CHOICES = new CustomerChoices(null, List.of(), List.of());
    private static final ActivityTrace NO_TRACE = new ActivityTrace(List.of(), List.of(), List.of(), List.of());

    @Mock
    private UserRepository userRepository;
    @Mock
    private AddressRepository addressRepository;
    @Mock
    private UserSettingsRepository settingsRepository;
    @Mock
    private UserConsentRepository consentRepository;
    @Mock
    private ConsentHistoryRepository historyRepository;
    @Mock
    private UserActivityRepository activityRepository;
    @Mock
    private EmailMessageRepository emailRepository;
    @Mock
    private AnalyticsEventRepository analyticsRepository;
    @Mock
    private PrivacyAuditEventRepository auditRepository;
    @Mock
    private PrivacyAudit audit;

    @Test
    void noActiveAccountMeansNoSnapshotAndNoFurtherReads() {
        final AtomicInteger reads = new AtomicInteger();
        final JpaSubjectSnapshotSource source = new JpaSubjectSnapshotSource(userRepository, addressRepository,
                id -> {
                    reads.incrementAndGet();
                    return NO_CHOICES;
                }, id -> {
                    reads.incrementAndGet();
                    return NO_TRACE;
                });
        when(userRepository.findActiveById(SUBJECT_ID)).thenReturn(Optional.empty());

        assertThat(source.load(SUBJECT_ID)).isEmpty();
        assertThat(reads).hasValue(0);
    }

    @Test
    void theSnapshotCombinesTheAccountWithBothOtherSources() {
        final User user = new User();
        user.setId(SUBJECT_ID);
        when(userRepository.findActiveById(SUBJECT_ID)).thenReturn(Optional.of(user));
        when(addressRepository.findByUserIdOrderById(SUBJECT_ID)).thenReturn(List.of());
        final JpaSubjectSnapshotSource source = new JpaSubjectSnapshotSource(userRepository, addressRepository,
                id -> NO_CHOICES, id -> NO_TRACE);

        final SubjectRecords records = source.load(SUBJECT_ID).orElseThrow();

        assertThat(records.user()).isSameAs(user);
        assertThat(records.choices()).isSameAs(NO_CHOICES);
        assertThat(records.trace()).isSameAs(NO_TRACE);
    }

    @Test
    void choicesAreReadFromSettingsConsentsAndHistory() {
        when(settingsRepository.findById(SUBJECT_ID)).thenReturn(Optional.empty());
        when(consentRepository.findByUserIdOrderByPurpose(SUBJECT_ID)).thenReturn(List.of());
        when(historyRepository.findByUserIdOrderById(SUBJECT_ID)).thenReturn(List.of());

        final CustomerChoices choices = new JpaCustomerChoicesSource(settingsRepository, consentRepository,
                historyRepository).read(SUBJECT_ID);

        assertThat(choices).isEqualTo(NO_CHOICES);
    }

    @Test
    void privacyRequestsAreLookedUpByTheSubjectPseudonym() {
        when(activityRepository.findByUserIdOrderByOccurredAtDesc(SUBJECT_ID)).thenReturn(List.of());
        when(emailRepository.findByUserIdOrderByCreatedAtDesc(SUBJECT_ID)).thenReturn(List.of());
        when(analyticsRepository.findByUserIdOrderByOccurredAtDesc(SUBJECT_ID)).thenReturn(List.of());
        when(audit.subjectRef(SUBJECT_ID)).thenReturn("ref-42");
        when(auditRepository.findBySubjectRefOrderById("ref-42")).thenReturn(List.of());

        final ActivityTrace trace = new JpaActivityTraceSource(activityRepository, emailRepository,
                analyticsRepository, auditRepository, audit).read(SUBJECT_ID);

        assertThat(trace).isEqualTo(NO_TRACE);
        verify(auditRepository).findBySubjectRefOrderById("ref-42");
    }
}
