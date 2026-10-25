package com.plantshop.user.service;

import com.plantshop.user.dto.GatedActionResponse;
import com.plantshop.user.dto.MarketingEmailRequest;
import com.plantshop.user.entity.ConsentPurpose;
import com.plantshop.user.entity.EmailCategory;
import com.plantshop.user.entity.EmailMessage;
import com.plantshop.user.entity.User;
import com.plantshop.user.notification.EmailSender;
import com.plantshop.user.privacy.SubjectPseudonymizer;
import com.plantshop.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/** Campaign e-mails: lawful only with MARKETING_EMAIL consent, checked right before each send. */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingEmailService {

    private final UserRepository userRepository;
    private final ConsentPolicyGate gate;
    private final EmailSender emailSender;
    private final SubjectPseudonymizer pseudonymizer;

    @Transactional
    public GatedActionResponse send(MarketingEmailRequest request) {
        // the row lock makes a concurrent REVOKE either complete before this check or wait for this send
        Optional<User> recipient = userRepository.findByIdForUpdate(request.userId());
        GateDecision decision = gate.check(request.userId(), ConsentPurpose.MARKETING_EMAIL);
        if (!decision.allowed()) {
            log.info("Marketing e-mail not sent campaign={} subject={} reason={}", request.campaign(),
                    pseudonymizer.ref(request.userId()), decision.reason());
            return GatedActionResponse.denied(request.userId(), decision);
        }
        EmailMessage message = emailSender.send(recipient.orElseThrow(), EmailCategory.MARKETING,
                "campaign-" + request.campaign(), Map.of());
        return GatedActionResponse.allowed(request.userId(), decision, "EMAIL_QUEUED", message.getId());
    }
}
