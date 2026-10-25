package com.plantshop.user.service;

import com.plantshop.logging.CorrelationId;
import com.plantshop.user.dto.ConsentOverview;
import com.plantshop.user.dto.ConsentResponse;
import com.plantshop.user.dto.GrantConsentRequest;
import com.plantshop.user.dto.RevokeConsentRequest;
import com.plantshop.user.entity.AuditOutcome;
import com.plantshop.user.entity.ConsentAction;
import com.plantshop.user.entity.ConsentHistoryEntry;
import com.plantshop.user.entity.ConsentPurpose;
import com.plantshop.user.entity.ConsentSource;
import com.plantshop.user.entity.ConsentStatus;
import com.plantshop.user.entity.PrivacyEvent;
import com.plantshop.user.entity.User;
import com.plantshop.user.entity.UserConsent;
import com.plantshop.user.exception.InvalidRequestException;
import com.plantshop.user.exception.PolicyVersionMismatchException;
import com.plantshop.user.exception.SubjectNotFoundException;
import com.plantshop.user.mapper.ConsentMapper;
import com.plantshop.user.privacy.PrivacyAudit;
import com.plantshop.user.privacy.PrivacyProperties;
import com.plantshop.user.repository.ConsentHistoryRepository;
import com.plantshop.user.repository.UserConsentRepository;
import com.plantshop.user.repository.UserRepository;
import com.plantshop.user.security.AppUserPrincipal;
import com.plantshop.user.security.SubjectAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * GRANT and REVOKE of purpose-specific consent. Both are idempotent: repeating a command that is already
 * in effect changes nothing and writes no history entry, so the history never contradicts itself.
 */
@Service
@RequiredArgsConstructor
public class ConsentService {

    private final UserRepository userRepository;
    private final UserConsentRepository consentRepository;
    private final ConsentHistoryRepository historyRepository;
    private final SubjectAccessPolicy accessPolicy;
    private final PrivacyProperties properties;
    private final PrivacyAudit audit;

    @Transactional(readOnly = true)
    public ConsentOverview overview(AppUserPrincipal actor, Long subjectId) {
        requireAccess(actor, subjectId, PrivacyEvent.CONSENTS_VIEWED);
        Map<ConsentPurpose, UserConsent> stored = consentRepository.findByUserIdOrderByPurpose(subjectId).stream()
                .collect(Collectors.toMap(UserConsent::getPurpose, Function.identity()));
        List<ConsentResponse> consents = Arrays.stream(ConsentPurpose.values())
                .map(purpose -> ConsentMapper.toResponse(purpose, stored.get(purpose),
                        properties.currentPolicyVersion(purpose), null))
                .toList();
        return new ConsentOverview(subjectId, consents,
                historyRepository.findByUserIdOrderById(subjectId).stream().map(ConsentMapper::toResponse).toList());
    }

    @Transactional
    public ConsentResponse grant(AppUserPrincipal actor, Long subjectId, ConsentPurpose purpose,
                                 GrantConsentRequest request) {
        lockSubject(actor, subjectId, PrivacyEvent.CONSENT_GRANTED);
        requireCustomerChannel(request.source());
        String currentVersion = properties.currentPolicyVersion(purpose);
        if (!currentVersion.equals(request.policyVersion())) {
            throw new PolicyVersionMismatchException(purpose, currentVersion);
        }

        UserConsent consent = consentRepository.findByUserIdAndPurpose(subjectId, purpose)
                .orElseGet(() -> new UserConsent(subjectId, purpose));
        boolean changed = consent.grant(currentVersion, request.source(), Instant.now());
        if (changed) {
            consentRepository.save(consent);
            historyRepository.save(new ConsentHistoryEntry(consent, ConsentAction.GRANTED, CorrelationId.current()));
            audit.record(PrivacyEvent.CONSENT_GRANTED, actor.getId(), subjectId, AuditOutcome.SUCCESS);
        }
        return ConsentMapper.toResponse(purpose, consent, currentVersion, changed);
    }

    @Transactional
    public ConsentResponse revoke(AppUserPrincipal actor, Long subjectId, ConsentPurpose purpose,
                                  RevokeConsentRequest request) {
        lockSubject(actor, subjectId, PrivacyEvent.CONSENT_WITHDRAWN);
        requireCustomerChannel(request.source());

        UserConsent consent = consentRepository.findByUserIdAndPurpose(subjectId, purpose).orElse(null);
        boolean changed = consent != null && consent.withdraw(request.source(), Instant.now());
        if (changed) {
            historyRepository.save(new ConsentHistoryEntry(consent, ConsentAction.WITHDRAWN, CorrelationId.current()));
            audit.record(PrivacyEvent.CONSENT_WITHDRAWN, actor.getId(), subjectId, AuditOutcome.SUCCESS);
        }
        return ConsentMapper.toResponse(purpose, consent, properties.currentPolicyVersion(purpose), changed);
    }

    /** Part of the erasure workflow: withdraws every granted consent with source SYSTEM. */
    @Transactional
    public void withdrawAll(Long userId, Instant now) {
        for (UserConsent consent : consentRepository.findByUserIdAndStatus(userId, ConsentStatus.GRANTED)) {
            consent.withdraw(ConsentSource.SYSTEM, now);
            historyRepository.save(new ConsentHistoryEntry(consent, ConsentAction.WITHDRAWN, CorrelationId.current()));
        }
    }

    private void requireAccess(AppUserPrincipal actor, Long subjectId, PrivacyEvent event) {
        if (!accessPolicy.canManageConsents(actor, subjectId)) {
            audit.record(event, actor.getId(), subjectId, AuditOutcome.DENIED);
            throw new SubjectNotFoundException();
        }
    }

    /** The row lock serializes this change with the gate checks of dependent operations (see UserRepository). */
    private User lockSubject(AppUserPrincipal actor, Long subjectId, PrivacyEvent event) {
        requireAccess(actor, subjectId, event);
        return userRepository.findByIdForUpdate(subjectId)
                .filter(User::isActive)
                .orElseThrow(SubjectNotFoundException::new);
    }

    private static void requireCustomerChannel(ConsentSource source) {
        if (source == ConsentSource.SYSTEM) {
            throw new InvalidRequestException("Source SYSTEM is reserved for the erasure workflow");
        }
    }
}
