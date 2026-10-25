package com.plantshop.user.repository;

import com.plantshop.user.entity.EmailCategory;
import com.plantshop.user.entity.EmailMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EmailMessageRepository extends JpaRepository<EmailMessage, Long> {

    List<EmailMessage> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndCategory(Long userId, EmailCategory category);

    @Modifying
    @Query("delete from EmailMessage m where m.userId = :userId")
    int deleteByUserId(Long userId);
}
