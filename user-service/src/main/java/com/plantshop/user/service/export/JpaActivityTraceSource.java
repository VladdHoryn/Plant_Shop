package com.plantshop.user.service.export;

import com.plantshop.user.privacy.PrivacyAudit;
import com.plantshop.user.repository.AnalyticsEventRepository;
import com.plantshop.user.repository.EmailMessageRepository;
import com.plantshop.user.repository.PrivacyAuditEventRepository;
import com.plantshop.user.repository.UserActivityRepository;
import org.springframework.stereotype.Component;

/**
 * The customer's activity trace from four tables. Privacy requests are stored under the subject's pseudonym,
 * so the audit component translates the id first. Joins the caller's transaction (see JpaSubjectSnapshotSource).
 */
@Component
public class JpaActivityTraceSource implements ActivityTraceSource {

    private final UserActivityRepository activityRepository;
    private final EmailMessageRepository emailRepository;
    private final AnalyticsEventRepository analyticsRepository;
    private final PrivacyAuditEventRepository auditRepository;
    private final PrivacyAudit audit;

    public JpaActivityTraceSource(final UserActivityRepository activityRepository,
                                  final EmailMessageRepository emailRepository,
                                  final AnalyticsEventRepository analyticsRepository,
                                  final PrivacyAuditEventRepository auditRepository, final PrivacyAudit audit) {
        this.activityRepository = activityRepository;
        this.emailRepository = emailRepository;
        this.analyticsRepository = analyticsRepository;
        this.auditRepository = auditRepository;
        this.audit = audit;
    }

    @Override
    public ActivityTrace read(final Long subjectId) {
        return new ActivityTrace(
                activityRepository.findByUserIdOrderByOccurredAtDesc(subjectId),
                emailRepository.findByUserIdOrderByCreatedAtDesc(subjectId),
                analyticsRepository.findByUserIdOrderByOccurredAtDesc(subjectId),
                auditRepository.findBySubjectRefOrderById(audit.subjectRef(subjectId)));
    }
}
