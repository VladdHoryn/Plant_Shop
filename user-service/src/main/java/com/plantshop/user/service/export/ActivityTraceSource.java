package com.plantshop.user.service.export;

/** Port: the customer's activity trace (activity log, e-mails, analytics, privacy requests). */
public interface ActivityTraceSource {

    ActivityTrace read(Long subjectId);
}
