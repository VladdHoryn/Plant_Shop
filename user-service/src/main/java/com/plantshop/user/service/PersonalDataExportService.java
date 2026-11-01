package com.plantshop.user.service;

import com.plantshop.user.client.OrderPrivacyClient;
import com.plantshop.user.client.OrderPrivacyClient.CustomerRef;
import com.plantshop.user.dto.PersonalDataExport;
import com.plantshop.user.dto.PersonalDataExport.ExcludedField;
import com.plantshop.user.dto.PersonalDataExport.Metadata;
import com.plantshop.user.dto.PersonalDataExport.Sections;
import com.plantshop.user.entity.AuditOutcome;
import com.plantshop.user.entity.PrivacyEvent;
import com.plantshop.user.entity.User;
import com.plantshop.user.exception.SubjectNotFoundException;
import com.plantshop.user.mapper.PersonalDataMapper;
import com.plantshop.user.privacy.ExportCoverage;
import com.plantshop.user.privacy.ExportOmissions;
import com.plantshop.user.privacy.ExportRequest;
import com.plantshop.user.privacy.ExportSection;
import com.plantshop.user.privacy.OrderHistory;
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

import java.util.List;

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
    public PersonalDataExport export(final AppUserPrincipal actor, final Long subjectId) {
        if (!accessPolicy.canHandleDataRequest(actor, subjectId)) {
            audit.record(PrivacyEvent.PERSONAL_DATA_EXPORT, actor.getId(), subjectId, AuditOutcome.DENIED);
            throw new SubjectNotFoundException();
        }
        // one consistent snapshot of the local tables; the remote call below runs outside the transaction
        final SubjectRecords records = readOnlyTransaction.execute(status -> loadLocalData(subjectId));
        if (records == null) {
            audit.record(PrivacyEvent.PERSONAL_DATA_EXPORT, actor.getId(), subjectId, AuditOutcome.NOT_FOUND);
            throw new SubjectNotFoundException();
        }

        final PersonalDataExport export = assemble(new ExportRequest(actor, subjectId), records,
                loadOrders(records.user().getEmail()));

        audit.record(PrivacyEvent.PERSONAL_DATA_EXPORT, actor.getId(), subjectId, AuditOutcome.SUCCESS);
        log.info("Personal data export generated exportId={} categories={} complete={}",
                export.metadata().exportId(), export.categories().size(), export.metadata().complete());
        return export;
    }

    private SubjectRecords loadLocalData(Long subjectId) {
        User user = userRepository.findById(subjectId).filter(User::isActive).orElse(null);
        if (user == null) {
            return null;
        }
        return new SubjectRecords(user,
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
    private OrderHistory loadOrders(final String email) {
        try {
            return OrderHistory.available(
                    orderClient.findOrders(new CustomerRef(email)).stream().map(PersonalDataMapper::toEntry).toList());
        } catch (FeignException e) {
            log.warn("Orders could not be read for a personal data export: {} status={}",
                    e.getClass().getSimpleName(), e.status());
            return OrderHistory.UNAVAILABLE;
        }
    }

    /**
     * Every intermediate result is an immutable value object: the sections, the omissions and the declared
     * categories are derived once and never changed afterwards (docs/reports/lab-07-report.md).
     */
    private PersonalDataExport assemble(final ExportRequest request, final SubjectRecords records,
                                        final OrderHistory orders) {
        final Sections sections = records.toSections(orders, properties);
        final ExportOmissions omissions = ExportOmissions.of(orders, sections);
        return new PersonalDataExport(Metadata.generated(request.requestedBy(), omissions.complete()),
                records.subject(), ExportCoverage.of(sections).categories(), ExportSection.SOURCES, sections,
                omissions.items(), EXCLUDED_FIELDS);
    }
}
