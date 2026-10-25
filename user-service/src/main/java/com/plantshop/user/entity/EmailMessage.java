package com.plantshop.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** A message handed to the mail provider. Template parameters (tokens, links) are not stored. */
@Entity
@Table(name = "email_outbox")
@Getter
@NoArgsConstructor
public class EmailMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailCategory category;

    @Column(nullable = false, length = 60)
    private String template;

    @Column(nullable = false)
    private String recipient;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public EmailMessage(Long userId, EmailCategory category, String template, String recipient) {
        this.userId = userId;
        this.category = category;
        this.template = template;
        this.recipient = recipient;
    }
}
