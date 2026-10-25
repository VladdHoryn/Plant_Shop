package com.plantshop.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.plantshop.user.client.OrderPrivacyClient.CustomerOrder;
import com.plantshop.user.client.OrderPrivacyClient.CustomerRef;
import com.plantshop.user.entity.User;
import com.plantshop.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Task 2: completeness, minimization, access control and log hygiene of the personal data export. */
class PersonalDataExportIntegrationTest extends IntegrationTestBase {

    private static final long UNKNOWN_ID = 987_654_321L;

    @Test
    void subjectExportsOwnDataFromEverySource() throws Exception {
        TestCustomer customer = createCustomerWithData("Export Owner");
        when(orderClient.findOrders(new CustomerRef(customer.email()))).thenReturn(List.of(controlOrder()));

        JsonNode export = json(mvc.perform(get("/api/users/{id}/personal-data", customer.id()).with(as(customer.user())))
                .andExpect(status().isOk()));

        // metadata: versioned schema, UTC timestamps, who asked
        assertThat(export.at("/metadata/schemaVersion").asText()).isEqualTo("1.0");
        assertThat(export.at("/metadata/timeZone").asText()).isEqualTo("UTC");
        assertThat(export.at("/metadata/generatedAt").asText()).endsWith("Z");
        assertThat(export.at("/metadata/requestedBy").asText()).isEqualTo("SUBJECT");
        assertThat(export.at("/metadata/complete").asBoolean()).isTrue();
        assertThat(export.at("/subject/userId").asLong()).isEqualTo(customer.id());
        assertThat(export.get("categories")).extracting(JsonNode::asText).contains("DIRECT_IDENTIFIERS", "CONTACT_DATA",
                "TECHNICAL_IDENTIFIERS", "PREFERENCES", "ACTIVITY_HISTORY", "CONSENT_RECORDS", "COMMUNICATIONS",
                "TRANSACTIONS");
        assertThat(export.get("sources").size()).isEqualTo(9);

        // a control record from every source
        JsonNode sections = export.get("sections");
        assertThat(sections.at("/profile/email").asText()).isEqualTo(customer.email());
        assertThat(sections.at("/profile/phone").asText()).isEqualTo(customer.phone());
        assertThat(sections.at("/profile/linkedAccount/subjectId").asText()).isEqualTo(customer.externalId());
        assertThat(sections.at("/addresses/0/line1").asText()).isEqualTo(customer.addressLine());
        assertThat(sections.at("/settings/language").asText()).isEqualTo("uk");
        assertThat(sections.at("/activity/0/details").asText()).isEqualTo(customer.activityDetails());
        assertThat(sections.at("/activity/0/ipAddress").asText()).isEqualTo(customer.ipAddress());
        assertThat(sections.at("/consents/current").findValuesAsText("status")).containsOnly("GRANTED");
        assertThat(sections.at("/consents/history").size()).isEqualTo(2);
        assertThat(sections.at("/communications/0/template").asText()).isEqualTo("control-template");
        assertThat(sections.at("/analytics/0/page").asText()).isEqualTo("/plants/control");
        assertThat(sections.at("/orders/0/orderId").asLong()).isEqualTo(9001L);
        assertThat(sections.at("/orders/0/items/0/plantName").asText()).isEqualTo("Control Monstera");
        assertThat(export.get("omissions")).isEmpty();
    }

    @Test
    void exportLeavesOutSecurityDataAndOtherCustomers() throws Exception {
        TestCustomer customer = createCustomerWithData("Export Minimal");
        TestCustomer stranger = createCustomerWithData("Export Stranger");
        User stored = userRepository.findById(customer.id()).orElseThrow();
        when(orderClient.findOrders(any())).thenReturn(List.of());

        String body = mvc.perform(get("/api/users/{id}/personal-data", customer.id()).with(as(customer.user())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$..passwordHash").isEmpty())
                .andExpect(jsonPath("$..passwordResetTokenHash").isEmpty())
                .andExpect(jsonPath("$..fraudReviewFlag").isEmpty())
                .andExpect(jsonPath("$..role").isEmpty())
                .andExpect(jsonPath("$.excludedFields[*].field").value(org.hamcrest.Matchers.hasItems(
                        "profile.passwordHash", "profile.passwordResetToken", "profile.fraudReviewFlag")))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(stored.getPasswordHash(), stored.getPasswordResetTokenHash(), "\"fraud");
        assertThat(body).doesNotContain(stranger.personalData());
    }

    @Test
    void anotherCustomerGetsTheSameAnswerAsForAMissingProfile(CapturedOutput output) throws Exception {
        TestCustomer victim = createCustomerWithData("Export Victim");
        User attacker = createUser("Export Attacker", UserRole.CUSTOMER);

        String foreign = mvc.perform(get("/api/users/{id}/personal-data", victim.id()).with(as(attacker)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String missing = mvc.perform(get("/api/users/{id}/personal-data", UNKNOWN_ID).with(as(attacker)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        // identical apart from "instance", which only echoes the requested path
        assertThat(withoutInstance(foreign)).isEqualTo(withoutInstance(missing));
        assertThat(foreign).doesNotContain(victim.personalData());
        assertThat(output.getOut())
                .doesNotContain(victim.personalData())
                .contains("event=\"PERSONAL_DATA_EXPORT\" outcome=\"DENIED\"");
        verify(orderClient, never()).findOrders(any());
    }

    @Test
    void privacyOfficerMayExportOnRequest() throws Exception {
        TestCustomer customer = createCustomerWithData("Export Officer Case");
        User officer = createUser("Export Officer", UserRole.PRIVACY_OFFICER);
        when(orderClient.findOrders(any())).thenReturn(List.of());

        mvc.perform(get("/api/users/{id}/personal-data", customer.id()).with(as(officer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.requestedBy").value("PRIVACY_OFFICER"))
                // the officer's identity is not part of the subject's export
                .andExpect(jsonPath("$..actor").isEmpty());
        mvc.perform(get("/api/users/{id}/personal-data", UNKNOWN_ID).with(as(officer)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mvc.perform(get("/api/users/{id}/personal-data", 1L)).andExpect(status().isUnauthorized());
    }

    @Test
    void missingSourcesAreDocumentedNotSkipped() throws Exception {
        User customer = createUser("Export Partial", UserRole.CUSTOMER);   // no settings stored
        when(orderClient.findOrders(any())).thenThrow(orderServiceDown());

        mvc.perform(get("/api/users/{id}/personal-data", customer.getId()).with(as(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.complete").value(false))
                .andExpect(jsonPath("$.sections.orders").isEmpty())
                .andExpect(jsonPath("$.sections.settings").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.sections.addresses").isArray())
                .andExpect(jsonPath("$.omissions[?(@.section == 'orders')].reason")
                        .value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.startsWith("SOURCE_UNAVAILABLE"))))
                .andExpect(jsonPath("$.omissions[?(@.section == 'settings')].reason").value("NO_DATA_STORED"));
    }

    @Test
    void theExportItselfIsNeverLogged(CapturedOutput output) throws Exception {
        TestCustomer customer = createCustomerWithData("Export Logging");
        when(orderClient.findOrders(any())).thenReturn(List.of(controlOrder()));

        mvc.perform(get("/api/users/{id}/personal-data", customer.id()).with(as(customer.user())))
                .andExpect(status().isOk());

        assertThat(output.getOut())
                .doesNotContain(customer.personalData())
                .doesNotContain("Control Monstera", "\"sections\"")
                .contains("Personal data export generated", "event=\"PERSONAL_DATA_EXPORT\" outcome=\"SUCCESS\"");
    }

    private String withoutInstance(String problemJson) throws Exception {
        JsonNode node = objectMapper.readTree(problemJson);
        ((com.fasterxml.jackson.databind.node.ObjectNode) node).remove("instance");
        return node.toString();
    }

    private static CustomerOrder controlOrder() {
        return new CustomerOrder(9001L, "CONFIRMED", "CARD", new BigDecimal("42.00"), "Order Street 1",
                Instant.parse("2026-10-01T10:00:00Z"),
                List.of(new CustomerOrder.Item(3L, "Control Monstera", 1, new BigDecimal("42.00"))));
    }
}
