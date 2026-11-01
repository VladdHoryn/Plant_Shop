package com.plantshop.user.service;

import com.plantshop.user.client.OrderPrivacyClient;
import com.plantshop.user.dto.PersonalDataExport;
import com.plantshop.user.entity.ConsentPurpose;
import com.plantshop.user.entity.User;
import com.plantshop.user.entity.UserRole;
import com.plantshop.user.privacy.PrivacyAudit;
import com.plantshop.user.privacy.PrivacyProperties;
import com.plantshop.user.repository.AddressRepository;
import com.plantshop.user.repository.AnalyticsEventRepository;
import com.plantshop.user.repository.ConsentHistoryRepository;
import com.plantshop.user.repository.EmailMessageRepository;
import com.plantshop.user.repository.PrivacyAuditEventRepository;
import com.plantshop.user.repository.UserActivityRepository;
import com.plantshop.user.repository.UserConsentRepository;
import com.plantshop.user.repository.UserRepository;
import com.plantshop.user.repository.UserSettingsRepository;
import com.plantshop.user.security.AppUserPrincipal;
import com.plantshop.user.security.SubjectAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static com.plantshop.user.privacy.DataCategory.CONTACT_DATA;
import static com.plantshop.user.privacy.DataCategory.DIRECT_IDENTIFIERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Lab 7, task 2: the categories of personal data an export declares must match the sections that hold data.
 * No Spring context and no database: every source is a mock, so the test isolates the assembly of the export.
 */
@ExtendWith(MockitoExtension.class)
class PersonalDataExportServiceTest {

    private static final long SUBJECT_ID = 42L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private AddressRepository addressRepository;
    @Mock
    private UserSettingsRepository settingsRepository;
    @Mock
    private UserActivityRepository activityRepository;
    @Mock
    private UserConsentRepository consentRepository;
    @Mock
    private ConsentHistoryRepository historyRepository;
    @Mock
    private EmailMessageRepository emailRepository;
    @Mock
    private AnalyticsEventRepository analyticsRepository;
    @Mock
    private PrivacyAuditEventRepository auditRepository;
    @Mock
    private OrderPrivacyClient orderClient;
    @Mock
    private SubjectAccessPolicy accessPolicy;
    @Mock
    private PrivacyAudit audit;
    @Mock
    private PlatformTransactionManager transactionManager;
    private PersonalDataExportService service;

    @BeforeEach
    void setUp() {
        PrivacyProperties properties = new PrivacyProperties("test-key",
                Map.of(ConsentPurpose.MARKETING_EMAIL, "v1", ConsentPurpose.OPTIONAL_ANALYTICS, "v1"));
        service = new PersonalDataExportService(userRepository, addressRepository, settingsRepository,
                activityRepository, consentRepository, historyRepository, emailRepository, analyticsRepository,
                auditRepository, orderClient, accessPolicy, properties, audit, transactionManager);
    }

    /**
     * A customer who has only a profile (no addresses, settings, orders...). The profile holds the e-mail and
     * the phone, so CONTACT_DATA must be declared even though the ADDRESSES section, which also holds contact
     * data, is empty. Leaving it out would tell the customer that no contact data is processed about them.
     */
    @Test
    void categoriesOfAnEmptySectionDoNotEraseThoseOfASectionWithData() {
        User user = customer();
        AppUserPrincipal actor = new AppUserPrincipal(user);
        when(accessPolicy.canHandleDataRequest(actor, SUBJECT_ID)).thenReturn(true);
        when(userRepository.findById(SUBJECT_ID)).thenReturn(Optional.of(user));

        PersonalDataExport export = service.export(actor, SUBJECT_ID);

        assertThat(export.sections().profile().email()).isEqualTo("profile.only@example.test");
        assertThat(export.sections().profile().phone()).isEqualTo("+380501112233");
        assertThat(export.sections().addresses()).isEmpty();
        assertThat(export.categories())
                .as("categories declared for a customer whose only data is the profile (e-mail, phone)")
                .containsExactly(DIRECT_IDENTIFIERS, CONTACT_DATA);
    }

    private static User customer() {
        User user = new User();
        user.setId(SUBJECT_ID);
        user.setFullName("Profile Only");
        user.setEmail("profile.only@example.test");
        user.setPhone("+380501112233");
        user.setRole(UserRole.CUSTOMER);
        user.setCreatedAt(Instant.parse("2026-10-01T10:00:00Z"));
        return user;
    }
}
