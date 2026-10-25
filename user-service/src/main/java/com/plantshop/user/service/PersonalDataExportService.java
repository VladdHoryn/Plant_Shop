package com.plantshop.user.service;

import com.plantshop.logging.CorrelationId;
import com.plantshop.user.client.OrderPrivacyClient;
import com.plantshop.user.client.OrderPrivacyClient.CustomerRef;
import com.plantshop.user.dto.ConsentHistoryResponse;
import com.plantshop.user.dto.ConsentResponse;
import com.plantshop.user.dto.PersonalDataExport;
import com.plantshop.user.dto.PersonalDataExport.Consents;
import com.plantshop.user.dto.PersonalDataExport.ExcludedField;
import com.plantshop.user.dto.PersonalDataExport.Metadata;
import com.plantshop.user.dto.PersonalDataExport.Omission;
import com.plantshop.user.dto.PersonalDataExport.OrderEntry;
import com.plantshop.user.dto.PersonalDataExport.Sections;
import com.plantshop.user.dto.PersonalDataExport.Subject;
import com.plantshop.user.entity.Address;
import com.plantshop.user.entity.AnalyticsEvent;
import com.plantshop.user.entity.AuditOutcome;
import com.plantshop.user.entity.ConsentHistoryEntry;
import com.plantshop.user.entity.ConsentPurpose;
import com.plantshop.user.entity.EmailMessage;
import com.plantshop.user.entity.PrivacyAuditEvent;
import com.plantshop.user.entity.PrivacyEvent;
import com.plantshop.user.entity.User;
import com.plantshop.user.entity.UserActivity;
import com.plantshop.user.entity.UserConsent;
import com.plantshop.user.entity.UserSettings;
import com.plantshop.user.exception.SubjectNotFoundException;
import com.plantshop.user.mapper.ConsentMapper;
import com.plantshop.user.mapper.PersonalDataMapper;
import com.plantshop.user.privacy.DataCategory;
import com.plantshop.user.privacy.ExportSection;
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
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Right of access: everything the shop holds about one customer, from every source in
 * {@link ExportSection}, in the versioned {@link PersonalDataExport} format.
 */
@Slf4j
@Service
public class PersonalDataExportService {

    /** Held about the customer but deliberately not exported (documented in the export itself). */
    static final List<ExcludedField> EXCLUDED_FIELDS = List.of(
            new ExcludedField("profile.passwordHash", "SECURITY_CREDENTIAL"),
            new ExcludedField("profile.emailVerificationToken", "SECURITY_CREDENTIAL"),
            new ExcludedField("profile.passwordResetToken", "SECURITY_CREDENTIAL"),
            new ExcludedField("profile.fraudReviewFlag", "INTERNAL_SECURITY_CONTROL"),
            new ExcludedField("privacyRequests.actor", "OTHER_PERSONS_DATA"));

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final UserSettingsRepository settingsRepository;
    private final UserActivityRepository activityRepository;
    private final UserConsentRepository consentRepository;
    private final ConsentHistoryRepository historyRepository;
    private final EmailMessageRepository emailRepository;
    private final AnalyticsEventRepository analyticsRepository;
    private final PrivacyAuditEventRepository auditRepository;
    private final OrderPrivacyClient orderClient;
    private final SubjectAccessPolicy accessPolicy;
    private final PrivacyProperties properties;
    private final PrivacyAudit audit;
    private final TransactionTemplate readOnlyTransaction;

    public PersonalDataExportService(UserRepository userRepository, AddressRepository addressRepository,
                                     UserSettingsRepository settingsRepository,
                                     UserActivityRepository activityRepository,
                                     UserConsentRepository consentRepository,
                                     ConsentHistoryRepository historyRepository,
                                     EmailMessageRepository emailRepository,
                                     AnalyticsEventRepository analyticsRepository,
                                     PrivacyAuditEventRepository auditRepository, OrderPrivacyClient orderClient,
                                     SubjectAccessPolicy accessPolicy, PrivacyProperties properties,
                                     PrivacyAudit audit, PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.settingsRepository = settingsRepository;
        this.activityRepository = activityRepository;
        this.consentRepository = consentRepository;
        this.historyRepository = historyRepository;
        this.emailRepository = emailRepository;
        this.analyticsRepository = analyticsRepository;
        this.auditRepository = auditRepository;
        this.orderClient = orderClient;
        this.accessPolicy = accessPolicy;
        this.properties = properties;
        this.audit = audit;
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
    }

    /**
     * Another customer's id and an unknown id produce the same {@link SubjectNotFoundException}, so the
     * response does not tell whether that profile exists. Only the fact of the export is logged, never
     * its content.
     */
    public PersonalDataExport export(AppUserPrincipal actor, Long subjectId) {
        if (!accessPolicy.canHandleDataRequest(actor, subjectId)) {
            audit.record(PrivacyEvent.PERSONAL_DATA_EXPORT, actor.getId(), subjectId, AuditOutcome.DENIED);
            throw new SubjectNotFoundException();
        }
        // one consistent snapshot of the local tables; the remote call below runs outside the transaction
        LocalData local = readOnlyTransaction.execute(status -> loadLocalData(subjectId));
        if (local == null) {
            audit.record(PrivacyEvent.PERSONAL_DATA_EXPORT, actor.getId(), subjectId, AuditOutcome.NOT_FOUND);
            throw new SubjectNotFoundException();
        }

        List<Omission> omissions = new ArrayList<>();
        List<OrderEntry> orders = loadOrders(local.user().getEmail(), omissions);
        if (local.settings() == null) {
            omissions.add(new Omission(ExportSection.SETTINGS.key(), "NO_DATA_STORED"));
        }
        PersonalDataExport export = assemble(actor, local, orders, omissions);

        audit.record(PrivacyEvent.PERSONAL_DATA_EXPORT, actor.getId(), subjectId, AuditOutcome.SUCCESS);
        log.info("Personal data export generated exportId={} categories={} complete={}",
                export.metadata().exportId(), export.categories().size(), export.metadata().complete());
        return export;
    }

    private LocalData loadLocalData(Long subjectId) {
        User user = userRepository.findById(subjectId).filter(User::isActive).orElse(null);
        if (user == null) {
            return null;
        }
        return new LocalData(user,
                addressRepository.findByUserIdOrderById(subjectId),
                settingsRepository.findById(subjectId).orElse(null),
                activityRepository.findByUserIdOrderByOccurredAtDesc(subjectId),
                consentRepository.findByUserIdOrderByPurpose(subjectId),
                historyRepository.findByUserIdOrderById(subjectId),
                emailRepository.findByUserIdOrderByCreatedAtDesc(subjectId),
                analyticsRepository.findByUserIdOrderByOccurredAtDesc(subjectId),
                auditRepository.findBySubjectRefOrderById(audit.subjectRef(subjectId)));
    }

    /** An unavailable source is reported as an omission instead of being silently left out. */
    private List<OrderEntry> loadOrders(String email, List<Omission> omissions) {
        try {
            return orderClient.findOrders(new CustomerRef(email)).stream().map(PersonalDataMapper::toEntry).toList();
        } catch (FeignException e) {
            log.warn("Orders could not be read for a personal data export: {} status={}",
                    e.getClass().getSimpleName(), e.status());
            omissions.add(new Omission(ExportSection.ORDERS.key(), "SOURCE_UNAVAILABLE: order-service did not answer, "
                    + "repeat the request later"));
            return List.of();
        }
    }

    private PersonalDataExport assemble(AppUserPrincipal actor, LocalData local, List<OrderEntry> orders,
                                        List<Omission> omissions) {
        User user = local.user();
        Map<ConsentPurpose, UserConsent> consents = local.consents().stream()
                .collect(Collectors.toMap(UserConsent::getPurpose, Function.identity()));
        List<ConsentResponse> currentConsents = Arrays.stream(ConsentPurpose.values())
                .map(p -> ConsentMapper.toResponse(p, consents.get(p), properties.currentPolicyVersion(p), null))
                .toList();
        List<ConsentHistoryResponse> consentHistory =
                local.consentHistory().stream().map(ConsentMapper::toResponse).toList();

        Sections sections = new Sections(
                PersonalDataMapper.toProfile(user),
                local.addresses().stream().map(PersonalDataMapper::toEntry).toList(),
                local.settings() == null ? null : PersonalDataMapper.toSettings(local.settings()),
                local.activity().stream().map(PersonalDataMapper::toEntry).toList(),
                new Consents(currentConsents, consentHistory),
                local.communications().stream().map(PersonalDataMapper::toEntry).toList(),
                local.analytics().stream().map(PersonalDataMapper::toEntry).toList(),
                orders,
                local.privacyRequests().stream().map(PersonalDataMapper::toEntry).toList());

        Map<ExportSection, Boolean> hasData = new LinkedHashMap<>();
        hasData.put(ExportSection.PROFILE, true);
        hasData.put(ExportSection.ADDRESSES, !sections.addresses().isEmpty());
        hasData.put(ExportSection.SETTINGS, sections.settings() != null);
        hasData.put(ExportSection.ACTIVITY, !sections.activity().isEmpty());
        hasData.put(ExportSection.CONSENTS, !consentHistory.isEmpty());
        hasData.put(ExportSection.COMMUNICATIONS, !sections.communications().isEmpty());
        hasData.put(ExportSection.ANALYTICS, !sections.analytics().isEmpty());
        hasData.put(ExportSection.ORDERS, !orders.isEmpty());
        hasData.put(ExportSection.PRIVACY_REQUESTS, !sections.privacyRequests().isEmpty());

        Set<DataCategory> categories = EnumSet.noneOf(DataCategory.class);
        hasData.forEach((section, present) -> {
            if (present) {
                categories.addAll(section.categories());
            }
        });
        Map<String, String> sources = new LinkedHashMap<>();
        for (ExportSection section : ExportSection.values()) {
            sources.put(section.key(), section.source());
        }

        boolean complete = omissions.stream().noneMatch(o -> o.reason().startsWith("SOURCE_UNAVAILABLE"));
        Metadata metadata = new Metadata(PersonalDataExport.SCHEMA_VERSION, UUID.randomUUID(), Instant.now(), "UTC",
                actor.isSubject(user.getId()) ? "SUBJECT" : "PRIVACY_OFFICER", CorrelationId.current(), complete);
        return new PersonalDataExport(metadata, new Subject(user.getId(), user.getStatus().name(), user.getCreatedAt()),
                List.copyOf(categories), sources, sections, omissions, EXCLUDED_FIELDS);
    }

    private record LocalData(User user, List<Address> addresses, UserSettings settings, List<UserActivity> activity,
                             List<UserConsent> consents, List<ConsentHistoryEntry> consentHistory,
                             List<EmailMessage> communications, List<AnalyticsEvent> analytics,
                             List<PrivacyAuditEvent> privacyRequests) {
    }
}
