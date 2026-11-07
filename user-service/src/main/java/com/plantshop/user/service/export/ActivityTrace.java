package com.plantshop.user.service.export;

import com.plantshop.user.entity.AnalyticsEvent;
import com.plantshop.user.entity.EmailMessage;
import com.plantshop.user.entity.PrivacyAuditEvent;
import com.plantshop.user.entity.UserActivity;

import java.util.List;

/**
 * What the customer did and what was done for them: activity log, e-mails, analytics events and privacy
 * requests. Immutable: the lists are copied when the trace is created.
 */
public record ActivityTrace(List<UserActivity> activity, List<EmailMessage> communications,
                            List<AnalyticsEvent> analytics, List<PrivacyAuditEvent> privacyRequests) {

    public ActivityTrace {
        activity = List.copyOf(activity);
        communications = List.copyOf(communications);
        analytics = List.copyOf(analytics);
        privacyRequests = List.copyOf(privacyRequests);
    }
}
