package com.plantshop.user.repository;

import com.plantshop.user.entity.PrivacyAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrivacyAuditEventRepository extends JpaRepository<PrivacyAuditEvent, Long> {

    List<PrivacyAuditEvent> findBySubjectRefOrderById(String subjectRef);

    long countBySubjectRef(String subjectRef);
}
