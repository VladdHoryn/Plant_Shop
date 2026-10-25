package com.plantshop.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.encoder.Encoder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;

import java.io.ByteArrayOutputStream;

import static com.plantshop.logging.PiiMaskerTest.API_KEY;
import static com.plantshop.logging.PiiMaskerTest.CORRELATION_ID;
import static com.plantshop.logging.PiiMaskerTest.EMAIL;
import static com.plantshop.logging.PiiMaskerTest.PASSWORD;
import static com.plantshop.logging.PiiMaskerTest.PHONE;
import static com.plantshop.logging.PiiMaskerTest.TOKEN;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Logs through a real logback pipeline. The scenario hides PII in a nested DTO, in the message of an
 * exception and of its cause; every assertion on the masked output fails if a single original
 * sensitive value reaches the log.
 */
class MaskingEncoderTest {

    /** Every original value that must never appear in a log, including the phone without separators. */
    private static final String[] SENSITIVE = {EMAIL, PHONE, "447700900123", "7700 900123", PASSWORD, TOKEN, API_KEY,
            "Olena Test", "Test Street"};

    private final LoggerContext context = new LoggerContext();
    private final ByteArrayOutputStream sink = new ByteArrayOutputStream();

    record Credentials(String password, String apiKey) {
    }

    record Contact(String email, String phone) {
    }

    record SignUp(String fullName, Contact contact, Credentials credentials, String shippingAddress) {
    }

    @BeforeEach
    void setUp() {
        // share the MDC used by org.slf4j.MDC, so the correlation id below reaches this context
        context.setMDCAdapter(MDC.getMDCAdapter());
        // StructuredLogEncoder reads its settings from the Spring Environment kept in the logger context
        context.putObject(Environment.class.getName(), new StandardEnvironment());
        MDC.put(CorrelationId.MDC_KEY, CORRELATION_ID);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        context.stop();
    }

    @Test
    void textLogMasksNestedDtoAndExceptionChain() {
        logFailedSignUp(logger(masking(textEncoder())));

        String output = sink.toString(UTF_8);
        assertThat(output).doesNotContain(SENSITIVE);
        assertThat(output).contains(
                CORRELATION_ID,                                   // diagnostics survive
                "o***@example.test", "+44********23", PiiMasker.REDACTED,
                "java.lang.IllegalStateException", "Caused by: java.lang.RuntimeException",
                "at com.plantshop.logging.MaskingEncoderTest.logFailedSignUp");
    }

    @Test
    void jsonLogStaysValidAndKeepsDiagnosticFields() throws Exception {
        logFailedSignUp(logger(masking(jsonEncoder())));

        String output = sink.toString(UTF_8);
        assertThat(output).doesNotContain(SENSITIVE);
        JsonNode line = new ObjectMapper().readTree(output.strip());
        assertThat(line.get("level").asText()).isEqualTo("ERROR");
        assertThat(line.get(CorrelationId.MDC_KEY).asText()).isEqualTo(CORRELATION_ID);
        assertThat(line.get("operation").asText()).isEqualTo("SIGN_UP");
        assertThat(line.get("message").asText()).contains("o***@example.test", "fullName=[REDACTED]");
        assertThat(line.get("stack_trace").asText())
                .contains("IllegalStateException", "apiKey=[REDACTED]", "token=[REDACTED]");
    }

    /**
     * Control: the same scenario without the masking encoder leaks every value, so the assertions above
     * really detect a leak and would turn red if a masking rule stopped working.
     */
    @Test
    void withoutMaskingEverySensitiveValueLeaks() {
        logFailedSignUp(logger(textEncoder()));

        assertThat(sink.toString(UTF_8)).contains(EMAIL, PHONE, PASSWORD, TOKEN, API_KEY, "Olena Test", "Test Street");
    }

    private void logFailedSignUp(Logger logger) {
        SignUp request = new SignUp("Olena Test", new Contact(EMAIL, PHONE), new Credentials(PASSWORD, API_KEY),
                "1 Test Street, Kyiv");
        Exception failure = new IllegalStateException("Mail provider rejected " + EMAIL + " (apiKey=" + API_KEY + ")",
                new RuntimeException("retry with token=" + TOKEN + " for " + PHONE));
        logger.atError()
                .addKeyValue("operation", "SIGN_UP")
                .setMessage("Sign-up failed for {} request={}")
                .addArgument(EMAIL)
                .addArgument(request)
                .setCause(failure)
                .log();
    }

    private Logger logger(Encoder<ILoggingEvent> encoder) {
        OutputStreamAppender<ILoggingEvent> appender = new OutputStreamAppender<>();
        appender.setContext(context);
        appender.setEncoder(encoder);
        appender.setOutputStream(sink);
        appender.start();
        Logger logger = context.getLogger("signup");
        logger.setAdditive(false);
        logger.setLevel(Level.DEBUG);
        logger.addAppender(appender);
        return logger;
    }

    private Encoder<ILoggingEvent> masking(Encoder<ILoggingEvent> delegate) {
        MaskingEncoder encoder = new MaskingEncoder();
        encoder.setContext(context);
        encoder.setDelegate(delegate);
        encoder.start();
        return encoder;
    }

    private Encoder<ILoggingEvent> textEncoder() {
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern("%d %level [%X{correlationId}] %logger : %m %kvp%n%ex");
        encoder.setCharset(UTF_8);
        encoder.start();
        return encoder;
    }

    private Encoder<ILoggingEvent> jsonEncoder() {
        StructuredLogEncoder encoder = new StructuredLogEncoder();
        encoder.setContext(context);
        encoder.setFormat("logstash");
        encoder.setCharset(UTF_8);
        encoder.start();
        return encoder;
    }
}
