package com.plantshop.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.plantshop.user.client.OrderPrivacyClient.OrdersAnonymized;
import com.plantshop.user.entity.ConsentPurpose;
import com.plantshop.user.entity.EmailCategory;
import com.plantshop.user.entity.User;
import com.plantshop.user.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Task 4: consent decides the outcome of the dependent business operation, purpose by purpose. */
class ConsentIntegrationTest extends IntegrationTestBase {

    private User customer;
    private User marketer;
    private String marketingVersion;

    @BeforeEach
    void setUp() {
        customer = createUser("Consent Customer", UserRole.CUSTOMER);
        marketer = createUser("Consent Marketer", UserRole.MARKETING);
        marketingVersion = privacyProperties.currentPolicyVersion(ConsentPurpose.MARKETING_EMAIL);
    }

    /** GRANT -> ALLOW (side effect recorded) -> REVOKE -> DENY (no side effect). */
    @Test
    void grantAllowRevokeDeny() throws Exception {
        // initial state: no consent, the campaign e-mail is not sent
        sendCampaign().andExpect(jsonPath("$.decision").value("DENY")).andExpect(jsonPath("$.reason").value("NO_CONSENT"))
                .andExpect(jsonPath("$.sideEffect").value("NONE"));
        assertThat(marketingEmails()).isZero();

        grant(ConsentPurpose.MARKETING_EMAIL, marketingVersion)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("GRANTED"))
                .andExpect(jsonPath("$.policyVersion").value(marketingVersion))
                .andExpect(jsonPath("$.source").value("WEB"))
                .andExpect(jsonPath("$.grantedAt").isNotEmpty())
                .andExpect(jsonPath("$.changed").value(true));

        sendCampaign().andExpect(jsonPath("$.decision").value("ALLOW"))
                .andExpect(jsonPath("$.sideEffect").value("EMAIL_QUEUED"))
                .andExpect(jsonPath("$.recordId").isNumber());
        assertThat(marketingEmails()).isEqualTo(1);

        revoke(ConsentPurpose.MARKETING_EMAIL)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.withdrawnAt").isNotEmpty())
                .andExpect(jsonPath("$.changed").value(true));

        // the very next send is blocked and has no side effect
        sendCampaign().andExpect(jsonPath("$.decision").value("DENY"))
                .andExpect(jsonPath("$.reason").value("CONSENT_WITHDRAWN"))
                .andExpect(jsonPath("$.recordId").doesNotExist());
        assertThat(marketingEmails()).isEqualTo(1);

        // evidence: who accepted which policy version, when, through which channel, and when it was withdrawn
        JsonNode overview = json(mvc.perform(get("/api/users/{id}/consents", customer.getId()).with(as(customer)))
                .andExpect(status().isOk()));
        assertThat(overview.get("history").findValuesAsText("action")).containsExactly("GRANTED", "WITHDRAWN");
        assertThat(overview.get("history").findValuesAsText("policyVersion")).containsOnly(marketingVersion);
    }

    @Test
    void consentForOnePurposeDoesNotAllowAnother() throws Exception {
        grant(ConsentPurpose.OPTIONAL_ANALYTICS, privacyProperties.currentPolicyVersion(ConsentPurpose.OPTIONAL_ANALYTICS))
                .andExpect(status().isOk());

        sendCampaign().andExpect(jsonPath("$.decision").value("DENY")).andExpect(jsonPath("$.reason").value("NO_CONSENT"));
        track().andExpect(jsonPath("$.decision").value("ALLOW")).andExpect(jsonPath("$.sideEffect").value("EVENT_RECORDED"));
        assertThat(marketingEmails()).isZero();
        assertThat(analyticsRepository.countByUserId(customer.getId())).isEqualTo(1);
    }

    @Test
    void analyticsStopsRightAfterRevoke() throws Exception {
        grant(ConsentPurpose.OPTIONAL_ANALYTICS, privacyProperties.currentPolicyVersion(ConsentPurpose.OPTIONAL_ANALYTICS));
        track().andExpect(jsonPath("$.decision").value("ALLOW"));

        revoke(ConsentPurpose.OPTIONAL_ANALYTICS);

        track().andExpect(jsonPath("$.decision").value("DENY")).andExpect(jsonPath("$.reason").value("CONSENT_WITHDRAWN"));
        assertThat(analyticsRepository.countByUserId(customer.getId())).isEqualTo(1);
    }

    @Test
    void consentToAnOutdatedPolicyVersionIsNotEnough() throws Exception {
        grant(ConsentPurpose.MARKETING_EMAIL, "marketing-v1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentPolicyVersion").value(marketingVersion));

        grant(ConsentPurpose.MARKETING_EMAIL, marketingVersion).andExpect(status().isOk());
        // the policy text changes after the customer accepted it
        jdbc.update("update users.user_consents set policy_version = 'marketing-v1' where user_id = ?", customer.getId());

        sendCampaign().andExpect(jsonPath("$.decision").value("DENY"))
                .andExpect(jsonPath("$.reason").value("POLICY_VERSION_OUTDATED"));
        assertThat(marketingEmails()).isZero();
    }

    @Test
    void repeatedCommandsAreIdempotent() throws Exception {
        grant(ConsentPurpose.MARKETING_EMAIL, marketingVersion).andExpect(jsonPath("$.changed").value(true));
        grant(ConsentPurpose.MARKETING_EMAIL, marketingVersion).andExpect(jsonPath("$.changed").value(false));
        revoke(ConsentPurpose.MARKETING_EMAIL).andExpect(jsonPath("$.changed").value(true));
        revoke(ConsentPurpose.MARKETING_EMAIL).andExpect(status().isOk()).andExpect(jsonPath("$.changed").value(false));
        // revoking something never given is a no-op too
        revoke(ConsentPurpose.OPTIONAL_ANALYTICS).andExpect(jsonPath("$.status").value("NOT_GIVEN"))
                .andExpect(jsonPath("$.changed").value(false));

        assertThat(historyRepository.countByUserId(customer.getId())).isEqualTo(2);
        assertThat(consentRepository.countByUserId(customer.getId())).isEqualTo(1);
    }

    @Test
    void invalidPurposeOrSourceIsRejected() throws Exception {
        perform(post("/api/users/{id}/consents/{purpose}/grant", customer.getId(), "NEWSLETTER_SMS").with(as(customer)),
                Map.of("policyVersion", marketingVersion, "source", "WEB"))
                .andExpect(status().isBadRequest());
        perform(post("/api/users/{id}/consents/{purpose}/grant", customer.getId(), "MARKETING_EMAIL").with(as(customer)),
                Map.of("policyVersion", marketingVersion, "source", "SYSTEM"))
                .andExpect(status().isBadRequest());
        assertThat(consentRepository.countByUserId(customer.getId())).isZero();
    }

    @Test
    void consentIsManagedOnlyByTheSubject() throws Exception {
        User other = createUser("Consent Other", UserRole.CUSTOMER);
        User officer = createUser("Consent Officer", UserRole.PRIVACY_OFFICER);

        for (User actor : new User[]{other, officer}) {
            perform(post("/api/users/{id}/consents/MARKETING_EMAIL/grant", customer.getId()).with(as(actor)),
                    Map.of("policyVersion", marketingVersion, "source", "WEB"))
                    .andExpect(status().isNotFound());
        }
        // only the MARKETING role may trigger campaign sends at all
        perform(post("/api/marketing/emails").with(as(customer)), Map.of("userId", customer.getId(), "campaign", "x"))
                .andExpect(status().isForbidden());
        assertThat(consentRepository.countByUserId(customer.getId())).isZero();
    }

    /** Consent is not a universal switch: contractual e-mails go out without any marketing consent. */
    @Test
    void contractualEmailIsNotBlockedByMissingMarketingConsent() throws Exception {
        perform(post("/api/users/password-reset"), Map.of("email", customer.getEmail())).andExpect(status().isAccepted());

        assertThat(emailRepository.countByUserIdAndCategory(customer.getId(), EmailCategory.TRANSACTIONAL)).isEqualTo(1);
        assertThat(marketingEmails()).isZero();
    }

    @Test
    void erasedSubjectIsDeniedEvenIfAJobStillReferencesIt() throws Exception {
        grant(ConsentPurpose.MARKETING_EMAIL, marketingVersion);
        when(orderClient.anonymizeOrders(any())).thenReturn(new OrdersAnonymized(0));
        mvc.perform(post("/api/users/{id}/anonymize", customer.getId()).with(as(customer))).andExpect(status().isOk());

        sendCampaign().andExpect(jsonPath("$.decision").value("DENY"))
                .andExpect(jsonPath("$.reason").value("SUBJECT_INACTIVE"));
    }

    private ResultActions grant(ConsentPurpose purpose, String policyVersion) throws Exception {
        return perform(post("/api/users/{id}/consents/{purpose}/grant", customer.getId(), purpose).with(as(customer)),
                Map.of("policyVersion", policyVersion, "source", "WEB"));
    }

    private ResultActions revoke(ConsentPurpose purpose) throws Exception {
        return perform(post("/api/users/{id}/consents/{purpose}/revoke", customer.getId(), purpose).with(as(customer)),
                Map.of("source", "WEB"));
    }

    private ResultActions sendCampaign() throws Exception {
        return perform(post("/api/marketing/emails").with(as(marketer)),
                Map.of("userId", customer.getId(), "campaign", "autumn-sale"))
                .andExpect(status().isOk());
    }

    private ResultActions track() throws Exception {
        return perform(post("/api/analytics/events").with(as(customer)), Map.of("event", "plant_viewed", "page", "/plants/5"))
                .andExpect(status().isOk());
    }

    private long marketingEmails() {
        return emailRepository.countByUserIdAndCategory(customer.getId(), EmailCategory.MARKETING);
    }
}
