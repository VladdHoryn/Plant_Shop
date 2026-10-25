package com.plantshop.user.service;

import com.plantshop.logging.CorrelationId;
import com.plantshop.user.client.OrderPrivacyClient;
import com.plantshop.user.client.OrderPrivacyClient.CustomerRef;
import com.plantshop.user.dto.ErasureResponse;
import com.plantshop.user.dto.ErasureResponse.Step;
import com.plantshop.user.entity.AuditOutcome;
import com.plantshop.user.entity.PrivacyEvent;
import com.plantshop.user.entity.User;
import com.plantshop.user.exception.SourceUnavailableException;
import com.plantshop.user.exception.SubjectNotFoundException;
import com.plantshop.user.privacy.ErasureRule;
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
import com.plantshop.user.security.AppUserPrincipal;
import com.plantshop.user.security.SubjectAccessPolicy;
import feign.FeignException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Right to erasure, implemented as anonymization according to {@link ErasureRule}.
 * <p>
 * Two steps. (1) order-service anonymizes the orders; it runs first because orders are linked by
 * e-mail, which step 2 destroys, and it is idempotent. (2) One local transaction over all user-service
 * tables: either every table is processed or none is. If step 1 fails nothing has changed; if step 2
 * fails it rolls back. Either way the same request can simply be repeated (forward recovery): step 1
 * then finds no orders left and step 2 runs again. A request for an already anonymized subject changes
 * nothing and reports ALREADY_ANONYMIZED.
 */
@Service
public class ErasureService {

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final UserSettingsRepository settingsRepository;
    private final UserActivityRepository activityRepository;
    private final UserConsentRepository consentRepository;
    private final ConsentHistoryRepository historyRepository;
    private final EmailMessageRepository emailRepository;
    private final AnalyticsEventRepository analyticsRepository;
    private final PrivacyAuditEventRepository auditRepository;
    private final ConsentService consentService;
    private final OrderPrivacyClient orderClient;
    private final SubjectAccessPolicy accessPolicy;
    private final PrivacyAudit audit;
    private final TransactionTemplate transaction;

    public ErasureService(UserRepository userRepository, AddressRepository addressRepository,
                          UserSettingsRepository settingsRepository, UserActivityRepository activityRepository,
                          UserConsentRepository consentRepository, ConsentHistoryRepository historyRepository,
                          EmailMessageRepository emailRepository, AnalyticsEventRepository analyticsRepository,
                          PrivacyAuditEventRepository auditRepository, ConsentService consentService,
                          OrderPrivacyClient orderClient, SubjectAccessPolicy accessPolicy, PrivacyAudit audit,
                          PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.settingsRepository = settingsRepository;
        this.activityRepository = activityRepository;
        this.consentRepository = consentRepository;
        this.historyRepository = historyRepository;
        this.emailRepository = emailRepository;
        this.analyticsRepository = analyticsRepository;
        this.auditRepository = auditRepository;
        this.consentService = consentService;
        this.orderClient = orderClient;
        this.accessPolicy = accessPolicy;
        this.audit = audit;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public ErasureResponse anonymize(AppUserPrincipal actor, Long subjectId) {
        if (!accessPolicy.canHandleDataRequest(actor, subjectId)) {
            audit.record(PrivacyEvent.ERASURE, actor.getId(), subjectId, AuditOutcome.DENIED);
            throw new SubjectNotFoundException();
        }
        User user = userRepository.findById(subjectId).orElse(null);
        if (user == null) {
            audit.record(PrivacyEvent.ERASURE, actor.getId(), subjectId, AuditOutcome.NOT_FOUND);
            throw new SubjectNotFoundException();
        }
        if (!user.isActive()) {
            audit.record(PrivacyEvent.ERASURE, actor.getId(), subjectId, AuditOutcome.NO_CHANGE);
            return alreadyAnonymized(user);
        }

        int ordersAnonymized;
        try {
            ordersAnonymized = orderClient.anonymizeOrders(new CustomerRef(user.getEmail())).ordersAnonymized();
        } catch (FeignException e) {
            audit.record(PrivacyEvent.ERASURE, actor.getId(), subjectId, AuditOutcome.FAILED);
            throw new SourceUnavailableException("order-service", e);
        }

        try {
            return transaction.execute(status -> eraseLocalData(actor, subjectId, ordersAnonymized));
        } catch (RuntimeException e) {
            audit.record(PrivacyEvent.ERASURE, actor.getId(), subjectId, AuditOutcome.FAILED);
            throw e;
        }
    }

    private ErasureResponse eraseLocalData(AppUserPrincipal actor, Long subjectId, int ordersAnonymized) {
        // the lock keeps consent changes and gated operations out while the user is being erased
        User user = userRepository.findByIdForUpdate(subjectId).orElseThrow(SubjectNotFoundException::new);
        if (!user.isActive()) {
            return alreadyAnonymized(user);   // a concurrent request finished first
        }
        Instant now = Instant.now();
        Map<ErasureRule, Integer> affected = new EnumMap<>(ErasureRule.class);
        affected.put(ErasureRule.ADDRESSES, addressRepository.deleteByUserId(subjectId));
        affected.put(ErasureRule.SETTINGS, settingsRepository.deleteByUserId(subjectId));
        affected.put(ErasureRule.ACTIVITY, activityRepository.deleteByUserId(subjectId));
        affected.put(ErasureRule.EMAIL_OUTBOX, emailRepository.deleteByUserId(subjectId));
        affected.put(ErasureRule.ANALYTICS, analyticsRepository.detachFromUser(subjectId));
        consentService.withdrawAll(subjectId, now);
        user.anonymize(now);
        userRepository.flush();
        affected.put(ErasureRule.USERS, 1);
        affected.put(ErasureRule.ORDERS, ordersAnonymized);
        // the success event joins this transaction: it exists only if the erasure committed
        audit.record(PrivacyEvent.ERASURE, actor.getId(), subjectId, AuditOutcome.SUCCESS);

        affected.put(ErasureRule.CONSENTS, (int) consentRepository.countByUserId(subjectId));
        affected.put(ErasureRule.CONSENT_HISTORY, (int) historyRepository.countByUserId(subjectId));
        affected.put(ErasureRule.PRIVACY_AUDIT, (int) auditRepository.countBySubjectRef(audit.subjectRef(subjectId)));
        return new ErasureResponse(subjectId, "ANONYMIZED", now, CorrelationId.current(), steps(affected));
    }

    private ErasureResponse alreadyAnonymized(User user) {
        return new ErasureResponse(user.getId(), "ALREADY_ANONYMIZED", user.getAnonymizedAt(), CorrelationId.current(),
                steps(Map.of()));
    }

    private static List<Step> steps(Map<ErasureRule, Integer> affected) {
        return Arrays.stream(ErasureRule.values()).map(rule -> Step.of(rule, affected.get(rule))).toList();
    }
}
