package com.plantshop.user.service;

import com.plantshop.user.entity.ActivityType;
import com.plantshop.user.entity.AuditOutcome;
import com.plantshop.user.entity.EmailCategory;
import com.plantshop.user.entity.PrivacyEvent;
import com.plantshop.user.entity.User;
import com.plantshop.user.entity.UserActivity;
import com.plantshop.user.notification.EmailSender;
import com.plantshop.user.privacy.PrivacyAudit;
import com.plantshop.user.privacy.SecureTokens;
import com.plantshop.user.repository.UserActivityRepository;
import com.plantshop.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final Duration TOKEN_VALIDITY = Duration.ofMinutes(30);

    private final UserRepository userRepository;
    private final UserActivityRepository activityRepository;
    private final EmailSender emailSender;
    private final PrivacyAudit audit;

    /**
     * Sends a reset link. The caller gets the same answer whether or not the account exists, so the
     * endpoint cannot be used to find out which e-mails are registered.
     */
    @Transactional
    public void requestReset(String rawEmail, RequestOrigin origin) {
        Optional<User> account = userRepository.findByEmail(User.normalizeEmail(rawEmail)).filter(User::isActive);
        if (account.isEmpty()) {
            log.info("Password reset requested for an unknown or inactive account");
            return;
        }
        User user = account.get();
        String token = SecureTokens.generate();
        user.setPasswordResetTokenHash(SecureTokens.hash(token));
        user.setPasswordResetExpiresAt(Instant.now().plus(TOKEN_VALIDITY));
        activityRepository.save(new UserActivity(user.getId(), ActivityType.PASSWORD_RESET_REQUESTED,
                origin.ipAddress(), origin.userAgent()));
        emailSender.send(user, EmailCategory.TRANSACTIONAL, "password-reset", Map.of("resetToken", token));
        audit.record(PrivacyEvent.PASSWORD_RESET_REQUESTED, null, user.getId(), AuditOutcome.SUCCESS);
    }
}
