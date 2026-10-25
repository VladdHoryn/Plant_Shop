package com.plantshop.logging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.plantshop.logging.PiiMasker.REDACTED;
import static org.assertj.core.api.Assertions.assertThat;

/** Synthetic values only: example.test is a reserved domain, +44 7700 900xxx a range reserved for fiction. */
class PiiMaskerTest {

    static final String EMAIL = "olena.test@example.test";
    static final String PHONE = "+44 7700 900123";
    static final String PASSWORD = "Synthetic-Passw0rd!";
    static final String TOKEN = "tok_9f8e7d6c5b4a39281706";
    static final String API_KEY = "demo-api-key-0123456789";
    static final String CORRELATION_ID = "3f6c1d2e-0b9a-4c55-9d1e-7a8b9c0d1e2f";

    record Credentials(String password, String apiKey) {
    }

    record Contact(String email, String phone) {
    }

    record SignUp(String fullName, Contact contact, Credentials credentials, String shippingAddress) {
    }

    @Test
    void masksEmailKeepingFirstCharacterAndDomain() {
        assertThat(PiiMasker.mask("Registered " + EMAIL + " ok")).isEqualTo("Registered o***@example.test ok");
    }

    @Test
    void masksUrlEncodedEmail() {
        assertThat(PiiMasker.mask("GET /api/orders?email=olena.test%40example.test"))
                .isEqualTo("GET /api/orders?email=o***%40example.test");
    }

    @Test
    void masksPhoneKeepingCountryCodeAndLastTwoDigits() {
        assertThat(PiiMasker.mask("SMS to " + PHONE + " queued")).isEqualTo("SMS to +44********23 queued");
        assertThat(PiiMasker.mask("phone=07700900123")).isEqualTo("phone=*********23");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "password=" + PASSWORD,
            "password: " + PASSWORD,
            "newPassword = " + PASSWORD + " please",
            "accessToken=" + TOKEN,
            "refresh_token=" + TOKEN + "&grant_type=refresh",
            "x-api-key: " + API_KEY,
            "Authorization: Bearer " + TOKEN,
            "Authorization=Basic b2xlbmE6c2VjcmV0",
            "sessionSecret='" + PASSWORD + "'",
            "Cookie: SESSION=" + TOKEN + "; Path=/",
            "jdbc:postgresql://olena:" + PASSWORD + "@db.example.test:5432/app",
            "called with eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.c2lnbmF0dXJl",
            "upstream said: invalid Bearer " + TOKEN,
            "Field error in object 'signUp' on field 'password': rejected value [" + PASSWORD + "]; codes []"
    })
    void redactsSecretsCompletely(String line) {
        assertThat(PiiMasker.mask(line))
                .contains(REDACTED)
                .doesNotContain(PASSWORD, TOKEN, API_KEY, "b2xlbmE6c2VjcmV0", "c2lnbmF0dXJl");
    }

    @Test
    void redactsInsideJsonAndKeepsItValid() throws Exception {
        String json = """
                {"correlationId":"%s","user":{"email":"%s","phone":"%s","fullName":"Olena Test"},\
                "credentials":{"password":"%s","apiKey":"%s"},"accessToken" : "%s","expiresIn":3600,\
                "refreshToken":null,"operation":"SIGN_UP","amount":49.90}"""
                .formatted(CORRELATION_ID, EMAIL, PHONE, PASSWORD, API_KEY, TOKEN);

        JsonNode node = new ObjectMapper().readTree(PiiMasker.mask(json));

        assertThat(node.at("/user/email").asText()).isEqualTo("o***@example.test");
        assertThat(node.at("/user/phone").asText()).isEqualTo("+44********23");
        assertThat(node.at("/user/fullName").asText()).isEqualTo(REDACTED);
        assertThat(node.at("/credentials/password").asText()).isEqualTo(REDACTED);
        assertThat(node.at("/credentials/apiKey").asText()).isEqualTo(REDACTED);
        assertThat(node.at("/accessToken").asText()).isEqualTo(REDACTED);
        assertThat(node.at("/refreshToken").asText()).isEqualTo(REDACTED);
        // diagnostic fields survive
        assertThat(node.at("/correlationId").asText()).isEqualTo(CORRELATION_ID);
        assertThat(node.at("/operation").asText()).isEqualTo("SIGN_UP");
        assertThat(node.at("/expiresIn").asInt()).isEqualTo(3600);
        assertThat(node.at("/amount").decimalValue()).isEqualByComparingTo("49.90");
    }

    @Test
    void redactsJsonEmbeddedAsStringInAJsonLogLine() throws Exception {
        String payload = "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(EMAIL, PASSWORD);
        String logLine = new ObjectMapper().createObjectNode().put("message", "Payload " + payload).toString();

        JsonNode node = new ObjectMapper().readTree(PiiMasker.mask(logLine));

        assertThat(node.get("message").asText())
                .isEqualTo("Payload {\"email\":\"o***@example.test\",\"password\":\"[REDACTED]\"}");
    }

    @Test
    void handlesNestedDtoToString() {
        SignUp signUp = new SignUp("Olena Test", new Contact(EMAIL, PHONE), new Credentials(PASSWORD, API_KEY),
                "1 Test Street, Kyiv");

        String masked = PiiMasker.mask("Sign-up " + signUp);

        assertThat(masked)
                .doesNotContain(EMAIL, PHONE, PASSWORD, API_KEY, "Olena Test", "Test Street", "Kyiv")
                .contains("fullName=[REDACTED]", "email=o***@example.test", "phone=+44********23",
                        "shippingAddress=[REDACTED]");
    }

    @Test
    void leavesTechnicalDataIntact() {
        String line = "2026-10-25T10:15:30.123+03:00 INFO 42 --- [main] [" + CORRELATION_ID + "] "
                + "c.p.user.service.PasswordResetService    : Order 7 total 30.00 status=CONFIRMED id=15\n"
                + "\tat com.plantshop.user.service.PasswordResetService.request(PasswordResetService.java:42)\n"
                + "HikariPool-1 - Added connection org.postgresql.jdbc.PgConnection@5f2b8e1";

        assertThat(PiiMasker.mask(line)).isEqualTo(line);
    }

    @Test
    void isIdempotent() {
        String line = "user=" + EMAIL + " phone=" + PHONE + " {\"phone\":\"" + PHONE + "\",\"token\":\"" + TOKEN
                + "\"} password=" + PASSWORD + " fullName=Olena Test";

        String once = PiiMasker.mask(line);

        assertThat(PiiMasker.mask(once)).isEqualTo(once);
    }
}
