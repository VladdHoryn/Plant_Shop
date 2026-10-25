package com.plantshop.user.service;

import com.plantshop.user.dto.RegisterUserRequest;
import com.plantshop.user.dto.UserResponse;
import com.plantshop.user.entity.ActivityType;
import com.plantshop.user.entity.AuditOutcome;
import com.plantshop.user.entity.EmailCategory;
import com.plantshop.user.entity.PrivacyEvent;
import com.plantshop.user.entity.User;
import com.plantshop.user.entity.UserActivity;
import com.plantshop.user.entity.UserSettings;
import com.plantshop.user.exception.EmailAlreadyRegisteredException;
import com.plantshop.user.mapper.UserMapper;
import com.plantshop.user.notification.EmailSender;
import com.plantshop.user.privacy.PrivacyAudit;
import com.plantshop.user.privacy.SecureTokens;
import com.plantshop.user.repository.UserActivityRepository;
import com.plantshop.user.repository.UserRepository;
import com.plantshop.user.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final UserRepository userRepository;
    private final UserSettingsRepository settingsRepository;
    private final UserActivityRepository activityRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final PrivacyAudit audit;

    /** Creates the account and sends the verification e-mail; that mail is contractual and needs no consent. */
    @Transactional
    public UserResponse register(RegisterUserRequest request, RequestOrigin origin) {
        String email = User.normalizeEmail(request.email());
        String phone = User.normalizePhone(request.phone());
        // Diagnostic line: the policy allows e-mail and phone only partially masked, secure-logging enforces it
        log.info("Registering account email={} phone={}", email, phone);
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        String verificationToken = SecureTokens.generate();
        user.setVerificationTokenHash(SecureTokens.hash(verificationToken));
        userRepository.save(user);

        settingsRepository.save(UserSettings.defaults(user.getId()));
        activityRepository.save(
                new UserActivity(user.getId(), ActivityType.ACCOUNT_CREATED, origin.ipAddress(), origin.userAgent()));
        emailSender.send(user, EmailCategory.TRANSACTIONAL, "verify-email", Map.of("verificationToken", verificationToken));
        audit.record(PrivacyEvent.USER_REGISTERED, null, user.getId(), AuditOutcome.SUCCESS);
        return UserMapper.toResponse(user);
    }
}
