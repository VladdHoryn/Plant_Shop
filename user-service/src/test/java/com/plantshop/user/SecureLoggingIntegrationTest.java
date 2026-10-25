package com.plantshop.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantshop.logging.CorrelationId;
import com.plantshop.user.entity.EmailCategory;
import com.plantshop.user.entity.User;
import com.plantshop.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Task 1. Real operations of the service run with known synthetic values; the captured console output
 * must contain the masked forms and must not contain a single original value. Any leak fails the test.
 */
class SecureLoggingIntegrationTest extends IntegrationTestBase {

    private static final String PHONE = "+44 7700 900987";
    private static final String PHONE_E164 = "+447700900987";
    private static final String NEW_PASSWORD = "Log-Check-Passw0rd!";
    private static final String CORRELATION_ID = "demo-correlation-0001";

    @Test
    void registrationLogsMaskedIdentifiersAndNoSecrets(CapturedOutput output) throws Exception {
        String email = "log.check." + UUID.randomUUID().toString().substring(0, 8) + "@example.test";

        mvc.perform(post("/api/users")
                        .header(CorrelationId.HEADER, CORRELATION_ID)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Log Check", "email", email,
                                "phone", PHONE, "password", NEW_PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(header().string(CorrelationId.HEADER, CORRELATION_ID));

        String verificationToken = sentParams("verify-email").get("verificationToken");
        String passwordHash = userRepository.findByEmail(email).orElseThrow().getPasswordHash();
        String log = output.getOut();

        assertThat(log).doesNotContain(email, PHONE, PHONE_E164, "447700900987", NEW_PASSWORD, passwordHash,
                verificationToken, "demo-provider-api-key-not-real", "Log Check");
        assertThat(log).contains(
                // partially masked identifiers keep their diagnostic value
                "Registering account email=l***@example.test phone=+44********87",
                // the wire log of the mail provider: header and token redacted, JSON still well-formed
                "headers={Authorization=[REDACTED], X-Correlation-Id=" + CORRELATION_ID + "}",
                "body={\"to\":\"l***@example.test\",\"template\":\"verify-email\",\"params\":{\"verificationToken\":\"[REDACTED]\"}}",
                // correlation id on every line of the request
                "[" + CORRELATION_ID + "]");
        // security event: fact, result, pseudonymous actor/subject, no personal data
        assertThat(log).containsPattern(
                "Security event event=\"USER_REGISTERED\" outcome=\"SUCCESS\" actor=\"anonymous\" subject=\"u_[0-9a-f]{16}\"");
    }

    @Test
    void passwordResetDoesNotLogTheResetToken(CapturedOutput output) throws Exception {
        User user = createUser("Reset Check", UserRole.CUSTOMER);

        perform(post("/api/users/password-reset"), Map.of("email", user.getEmail())).andExpect(status().isAccepted());

        String resetToken = sentParams("password-reset").get("resetToken");
        assertThat(output.getOut())
                .doesNotContain(user.getEmail(), user.getPhone(), user.getFullName(), resetToken)
                .contains("\"resetToken\":\"[REDACTED]\"", "event=\"PASSWORD_RESET_REQUESTED\"");
    }

    /**
     * Negative scenario: personal data and a token hidden in an exception message and its cause (a nested
     * JSON document), logged with the full stack trace by the generic error handler.
     */
    @Test
    void exceptionMessagesAreMaskedInTheStackTrace(CapturedOutput output) throws Exception {
        String email = "exception.check." + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
        AtomicReference<String> token = new AtomicReference<>();
        doAnswer(invocation -> {
            User recipient = invocation.getArgument(0);
            Map<String, String> params = invocation.getArgument(3);
            token.set(params.get("verificationToken"));
            throw new IllegalStateException("Mail provider rejected " + recipient.getEmail() + " (" + recipient.getPhone() + ")",
                    new RuntimeException("provider response: " + new ObjectMapper().writeValueAsString(
                            Map.of("to", recipient.getEmail(), "params", params, "apiKey", "demo-provider-api-key-not-real"))));
        }).when(emailSender).send(any(), any(), any(), any());

        perform(post("/api/users"), Map.of("fullName", "Exception Check", "email", email, "phone", PHONE,
                "password", NEW_PASSWORD))
                .andExpect(status().isInternalServerError());

        assertThat(token.get()).isNotBlank();
        assertThat(output.getOut())
                .doesNotContain(email, PHONE, PHONE_E164, NEW_PASSWORD, token.get(), "demo-provider-api-key-not-real")
                .contains("Unhandled error", "java.lang.IllegalStateException: Mail provider rejected e***@example.test",
                        "Caused by: java.lang.RuntimeException", "\"verificationToken\":\"[REDACTED]\"");
        // the failed registration left nothing behind
        assertThat(userRepository.findByEmail(email)).isEmpty();
    }

    private Map<String, String> sentParams(String template) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> params = ArgumentCaptor.forClass(Map.class);
        verify(emailSender).send(any(), eq(EmailCategory.TRANSACTIONAL), eq(template), params.capture());
        return params.getValue();
    }
}
