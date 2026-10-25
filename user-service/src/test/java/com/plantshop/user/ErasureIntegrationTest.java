package com.plantshop.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.plantshop.user.client.OrderPrivacyClient.CustomerRef;
import com.plantshop.user.client.OrderPrivacyClient.OrdersAnonymized;
import com.plantshop.user.entity.ConsentSource;
import com.plantshop.user.entity.ConsentStatus;
import com.plantshop.user.entity.User;
import com.plantshop.user.entity.UserRole;
import com.plantshop.user.entity.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Task 3: the erasure workflow checked against the database state before and after. */
class ErasureIntegrationTest extends IntegrationTestBase {

    @Test
    void removesEveryTraceOfTheCustomerAndKeepsRetainedRecordsIntact(CapturedOutput output) throws Exception {
        TestCustomer customer = createCustomerWithData("Erase Me");
        when(orderClient.anonymizeOrders(new CustomerRef(customer.email()))).thenReturn(new OrdersAnonymized(2));
        assertThat(everythingStored()).contains(customer.personalData());   // the "before" state

        JsonNode result = json(mvc.perform(post("/api/users/{id}/anonymize", customer.id()).with(as(customer.user())))
                .andExpect(status().isOk()));

        assertThat(result.get("status").asText()).isEqualTo("ANONYMIZED");
        assertThat(affected(result)).containsEntry("users", "ANONYMIZE:1")
                .containsEntry("user_addresses", "DELETE:1")
                .containsEntry("user_settings", "DELETE:1")
                .containsEntry("user_activity", "DELETE:1")
                .containsEntry("email_outbox", "DELETE:1")
                .containsEntry("analytics_events", "ANONYMIZE:1")
                .containsEntry("user_consents", "RETAIN:2")
                .containsEntry("consent_history", "RETAIN:4")
                .containsEntry("orders", "ANONYMIZE:2")
                .containsEntry("payments", "SKIP:null");
        verify(orderClient).anonymizeOrders(new CustomerRef(customer.email()));

        // no personal value of the customer is left in any table of the service, nor in the log
        assertThat(everythingStored()).doesNotContain(customer.personalData());
        assertThat(output.getOut()).doesNotContain(customer.personalData());

        // the user row is a tombstone: same id, random unique e-mail, nothing else
        User tombstone = userRepository.findById(customer.id()).orElseThrow();
        assertThat(tombstone.getStatus()).isEqualTo(UserStatus.ANONYMIZED);
        assertThat(tombstone.getEmail()).matches("deleted-[0-9a-f-]{36}@anon\\.invalid");
        assertThat(tombstone.getFullName()).isNull();
        assertThat(tombstone.getPhone()).isNull();
        assertThat(tombstone.getPasswordHash()).isNull();
        assertThat(tombstone.getPasswordResetTokenHash()).isNull();
        assertThat(tombstone.getExternalSubjectId()).isNull();
        assertThat(tombstone.isFraudReviewFlag()).isFalse();

        // retained records: consents withdrawn by the system, history kept, analytics detached and generalized
        assertThat(consentRepository.findByUserIdOrderByPurpose(customer.id()))
                .hasSize(2)
                .allSatisfy(c -> {
                    assertThat(c.getStatus()).isEqualTo(ConsentStatus.WITHDRAWN);
                    assertThat(c.getSource()).isEqualTo(ConsentSource.SYSTEM);
                });
        assertThat(historyRepository.countByUserId(customer.id())).isEqualTo(4);
        assertThat(analyticsRepository.countByUserId(customer.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from users.analytics_events where user_id is null "
                + "and page = '/plants/control' and occurred_at = date_trunc('day', occurred_at)", Integer.class))
                .isPositive();
        assertThat(jdbc.queryForObject("select count(*) from users.privacy_audit_events where subject_ref = ? "
                + "and event_type = 'ERASURE' and outcome = 'SUCCESS'", Integer.class, pseudonymizer.ref(customer.id())))
                .isEqualTo(1);

        // referential integrity and reporting queries still work
        assertThat(jdbc.queryForObject("select count(*) from users.consent_history h "
                + "left join users.users u on u.id = h.user_id where u.id is null", Integer.class)).isZero();
        assertThat(jdbc.queryForList("select u.status, c.purpose, c.status, count(*) from users.user_consents c "
                + "join users.users u on u.id = c.user_id group by u.status, c.purpose, c.status")).isNotEmpty();

        // the account can no longer be used, the gate denies all optional processing
        mvc.perform(get("/api/users/{id}/personal-data", customer.id()).with(as(customer.user())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void repeatingTheRequestChangesNothing() throws Exception {
        TestCustomer customer = createCustomerWithData("Erase Twice");
        User officer = createUser("Erase Officer", UserRole.PRIVACY_OFFICER);
        when(orderClient.anonymizeOrders(any())).thenReturn(new OrdersAnonymized(1));

        mvc.perform(post("/api/users/{id}/anonymize", customer.id()).with(as(officer))).andExpect(status().isOk());
        String tombstoneEmail = userRepository.findById(customer.id()).orElseThrow().getEmail();
        long users = userRepository.count();
        String stateAfterFirstRun = everythingStored();

        JsonNode repeat = json(mvc.perform(post("/api/users/{id}/anonymize", customer.id()).with(as(officer)))
                .andExpect(status().isOk()));

        assertThat(repeat.get("status").asText()).isEqualTo("ALREADY_ANONYMIZED");
        assertThat(userRepository.findById(customer.id()).orElseThrow().getEmail()).isEqualTo(tombstoneEmail);
        assertThat(userRepository.count()).isEqualTo(users);
        // only the audit trail grows (NO_CHANGE event); no personal data comes back
        assertThat(everythingStored()).doesNotContain(customer.personalData());
        assertThat(stateAfterFirstRun.lines().count()).isEqualTo(everythingStored().lines().count() - 1);
        verify(orderClient, times(1)).anonymizeOrders(any());
    }

    @Test
    void tombstonesOfDifferentUsersDoNotCollide() throws Exception {
        User officer = createUser("Erase Officer Two", UserRole.PRIVACY_OFFICER);
        TestCustomer first = createCustomerWithData("Erase First");
        TestCustomer second = createCustomerWithData("Erase Second");
        when(orderClient.anonymizeOrders(any())).thenReturn(new OrdersAnonymized(0));

        mvc.perform(post("/api/users/{id}/anonymize", first.id()).with(as(officer))).andExpect(status().isOk());
        mvc.perform(post("/api/users/{id}/anonymize", second.id()).with(as(officer))).andExpect(status().isOk());

        assertThat(userRepository.findById(first.id()).orElseThrow().getEmail())
                .isNotEqualTo(userRepository.findById(second.id()).orElseThrow().getEmail());
    }

    @Test
    void anotherCustomerCannotEraseAndLearnsNothing() throws Exception {
        TestCustomer victim = createCustomerWithData("Erase Victim");
        User attacker = createUser("Erase Attacker", UserRole.CUSTOMER);

        mvc.perform(post("/api/users/{id}/anonymize", victim.id()).with(as(attacker)))
                .andExpect(status().isNotFound());

        assertThat(userRepository.findById(victim.id()).orElseThrow().isActive()).isTrue();
        assertThat(everythingStored()).contains(victim.personalData());
        verify(orderClient, never()).anonymizeOrders(any());
    }

    @Test
    void aGetRequestNeverErases() throws Exception {
        TestCustomer customer = createCustomerWithData("Erase By Get");

        mvc.perform(get("/api/users/{id}/anonymize", customer.id()).with(as(customer.user())))
                .andExpect(status().isMethodNotAllowed());

        assertThat(userRepository.findById(customer.id()).orElseThrow().isActive()).isTrue();
        verify(orderClient, never()).anonymizeOrders(any());
    }

    @Test
    void orderServiceOutageChangesNothing() throws Exception {
        TestCustomer customer = createCustomerWithData("Erase Outage");
        when(orderClient.anonymizeOrders(any())).thenThrow(orderServiceDown());

        mvc.perform(post("/api/users/{id}/anonymize", customer.id()).with(as(customer.user())))
                .andExpect(status().isServiceUnavailable());

        assertThat(userRepository.findById(customer.id()).orElseThrow().isActive()).isTrue();
        assertThat(everythingStored()).contains(customer.personalData());
        assertThat(auditOutcomes(customer.id())).containsExactly("FAILED");
    }

    /** A failure in the middle of the local transaction rolls everything back; the repeated request completes. */
    @Test
    void partialFailureRollsBackAndTheRetryCompletes() throws Exception {
        TestCustomer customer = createCustomerWithData("Erase Rollback");
        when(orderClient.anonymizeOrders(any())).thenReturn(new OrdersAnonymized(1), new OrdersAnonymized(0));
        // addresses, settings, activity, outbox and analytics are already processed when this step fails
        doThrow(new IllegalStateException("simulated failure")).when(consentService).withdrawAll(anyLong(), any(Instant.class));

        mvc.perform(post("/api/users/{id}/anonymize", customer.id()).with(as(customer.user())))
                .andExpect(status().isInternalServerError());

        assertThat(userRepository.findById(customer.id()).orElseThrow().isActive()).isTrue();
        assertThat(everythingStored()).contains(customer.personalData());
        assertThat(addressRepository.findByUserIdOrderById(customer.id())).hasSize(1);

        doCallRealMethod().when(consentService).withdrawAll(anyLong(), any(Instant.class));
        mvc.perform(post("/api/users/{id}/anonymize", customer.id()).with(as(customer.user())))
                .andExpect(status().isOk());

        assertThat(everythingStored()).doesNotContain(customer.personalData());
        assertThat(auditOutcomes(customer.id())).containsExactly("FAILED", "SUCCESS");
    }

    private List<String> auditOutcomes(Long subjectId) {
        return jdbc.queryForList("select outcome from users.privacy_audit_events where subject_ref = ? "
                + "and event_type = 'ERASURE' order by id", String.class, pseudonymizer.ref(subjectId));
    }

    /** dataset -> "ACTION:affectedRecords" */
    private static Map<String, String> affected(JsonNode result) {
        Map<String, String> steps = new java.util.HashMap<>();
        result.get("steps").forEach(step -> steps.put(step.get("dataset").asText(),
                step.get("action").asText() + ":" + (step.get("affectedRecords").isNull() ? "null"
                        : step.get("affectedRecords").asText())));
        return steps;
    }
}
