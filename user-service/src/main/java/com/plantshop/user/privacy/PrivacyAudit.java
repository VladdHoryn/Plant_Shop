package com.plantshop.user.privacy;

import com.plantshop.logging.CorrelationId;
import com.plantshop.user.entity.AuditOutcome;
import com.plantshop.user.entity.PrivacyAuditEvent;
import com.plantshop.user.entity.PrivacyEvent;
import com.plantshop.user.repository.PrivacyAuditEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Security events: stored in privacy_audit_events and logged as key-value pairs. Each records the fact,
 * time, actor, subject, result and correlation id; actor and subject are pseudonyms, nothing else about
 * the person is written.
 */
@Slf4j
@Component
public class PrivacyAudit {

    private final PrivacyAuditEventRepository repository;
    private final SubjectPseudonymizer pseudonymizer;
    private final TransactionTemplate independentTransaction;

    public PrivacyAudit(PrivacyAuditEventRepository repository, SubjectPseudonymizer pseudonymizer,
                        PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.pseudonymizer = pseudonymizer;
        this.independentTransaction = new TransactionTemplate(transactionManager);
        this.independentTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * A success joins the caller's transaction, so the event exists only if the operation committed.
     * A rejection is stored in its own transaction, because the operation's transaction rolls back.
     */
    public void record(PrivacyEvent event, Long actorId, Long subjectId, AuditOutcome outcome) {
        PrivacyAuditEvent entry = new PrivacyAuditEvent(event, pseudonymizer.ref(actorId), pseudonymizer.ref(subjectId),
                outcome, CorrelationId.current());
        if (outcome.isRejection()) {
            independentTransaction.executeWithoutResult(status -> repository.save(entry));
        } else {
            repository.save(entry);
        }
        log.atInfo()
                .setMessage("Security event")
                .addKeyValue("event", event)
                .addKeyValue("outcome", outcome)
                .addKeyValue("actor", entry.getActorRef())
                .addKeyValue("subject", entry.getSubjectRef())
                .log();
    }

    public String subjectRef(Long subjectId) {
        return pseudonymizer.ref(subjectId);
    }
}
