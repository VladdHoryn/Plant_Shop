package com.plantshop.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantshop.user.client.OrderPrivacyClient;
import com.plantshop.user.entity.ActivityType;
import com.plantshop.user.entity.Address;
import com.plantshop.user.entity.AnalyticsEvent;
import com.plantshop.user.entity.ConsentAction;
import com.plantshop.user.entity.ConsentHistoryEntry;
import com.plantshop.user.entity.ConsentPurpose;
import com.plantshop.user.entity.ConsentSource;
import com.plantshop.user.entity.EmailCategory;
import com.plantshop.user.entity.EmailMessage;
import com.plantshop.user.entity.User;
import com.plantshop.user.entity.UserActivity;
import com.plantshop.user.entity.UserConsent;
import com.plantshop.user.entity.UserRole;
import com.plantshop.user.entity.UserSettings;
import com.plantshop.user.notification.EmailSender;
import com.plantshop.user.privacy.PrivacyProperties;
import com.plantshop.user.privacy.SubjectPseudonymizer;
import com.plantshop.user.repository.AddressRepository;
import com.plantshop.user.repository.AnalyticsEventRepository;
import com.plantshop.user.repository.ConsentHistoryRepository;
import com.plantshop.user.repository.EmailMessageRepository;
import com.plantshop.user.repository.UserActivityRepository;
import com.plantshop.user.repository.UserConsentRepository;
import com.plantshop.user.repository.UserRepository;
import com.plantshop.user.repository.UserSettingsRepository;
import com.plantshop.user.service.ConsentService;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

/**
 * One PostgreSQL container and one Spring context for all integration tests. order-service is replaced by
 * a mock; every fixture uses synthetic, per-test unique values (example.test e-mails, +44 7700 9xxxxx
 * numbers reserved for fiction, 2001:db8:: documentation addresses).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
public abstract class IntegrationTestBase {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    protected static final String PASSWORD = "Test-Passw0rd-2026";

    private static final AtomicInteger SEQUENCE = new AtomicInteger(20000);
    private static String passwordHash;

    @MockitoBean
    protected OrderPrivacyClient orderClient;
    @MockitoSpyBean
    protected EmailSender emailSender;
    @MockitoSpyBean
    protected ConsentService consentService;

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected PasswordEncoder passwordEncoder;
    @Autowired
    protected SubjectPseudonymizer pseudonymizer;
    @Autowired
    protected PrivacyProperties privacyProperties;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected AddressRepository addressRepository;
    @Autowired
    protected UserSettingsRepository settingsRepository;
    @Autowired
    protected UserActivityRepository activityRepository;
    @Autowired
    protected UserConsentRepository consentRepository;
    @Autowired
    protected ConsentHistoryRepository historyRepository;
    @Autowired
    protected EmailMessageRepository emailRepository;
    @Autowired
    protected AnalyticsEventRepository analyticsRepository;

    /** A customer with records in every user-service table, and the personal values stored about them. */
    protected record TestCustomer(User user, String email, String fullName, String phone, String addressLine,
                                  String activityDetails, String ipAddress, String userAgent, String externalId) {

        public Long id() {
            return user.getId();
        }

        public String[] personalData() {
            return new String[]{email, fullName, phone, addressLine, activityDetails, ipAddress, userAgent, externalId};
        }
    }

    protected User createUser(String name, UserRole role) {
        int n = SEQUENCE.incrementAndGet();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setFullName(name + " " + suffix);
        user.setEmail(name.toLowerCase().replace(' ', '.') + "." + suffix + "@example.test");
        user.setPhone("+4477009" + n);
        user.setPasswordHash(passwordHash());
        user.setEmailVerified(true);
        user.setRole(role);
        return userRepository.save(user);
    }

    protected TestCustomer createCustomerWithData(String name) {
        User user = createUser(name, UserRole.CUSTOMER);
        String suffix = user.getFullName().substring(user.getFullName().lastIndexOf(' ') + 1);
        String externalId = "google-test-" + suffix;
        user.setExternalProvider("google");
        user.setExternalSubjectId(externalId);
        user.setPasswordResetTokenHash("f".repeat(64));
        user.setFraudReviewFlag(true);
        userRepository.save(user);

        String addressLine = suffix + " Control Street";
        addressRepository.save(new Address(user.getId(), "home", user.getFullName(), addressLine, "Kharkiv", "61000", "UA"));
        settingsRepository.save(UserSettings.defaults(user.getId()));

        String ip = "2001:db8::" + Integer.toHexString(SEQUENCE.incrementAndGet());
        String userAgent = "ControlAgent/" + suffix;
        String details = "control search " + suffix;
        UserActivity activity = new UserActivity(user.getId(), ActivityType.SEARCH, ip, userAgent);
        activity.setDetails(details);
        activityRepository.save(activity);

        Instant now = Instant.now();
        for (ConsentPurpose purpose : ConsentPurpose.values()) {
            UserConsent consent = new UserConsent(user.getId(), purpose);
            consent.grant(privacyProperties.currentPolicyVersion(purpose), ConsentSource.WEB, now);
            consentRepository.save(consent);
            historyRepository.save(new ConsentHistoryEntry(consent, ConsentAction.GRANTED, "fixture"));
        }
        emailRepository.save(new EmailMessage(user.getId(), EmailCategory.MARKETING, "control-template", user.getEmail()));
        analyticsRepository.save(new AnalyticsEvent(user.getId(), "plant_viewed", "/plants/control"));

        return new TestCustomer(user, user.getEmail(), user.getFullName(), user.getPhone(), addressLine, details, ip,
                userAgent, externalId);
    }

    protected RequestPostProcessor as(User user) {
        return httpBasic(user.getEmail(), PASSWORD);
    }

    protected ResultActions perform(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    protected JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    /** Every row of every table in the user-service schema, rendered as text, for "no trace left" checks. */
    protected String everythingStored() {
        List<String> tables = jdbc.queryForList("select table_name from information_schema.tables "
                + "where table_schema = 'users' and table_type = 'BASE TABLE'", String.class);
        List<String> rows = new ArrayList<>();
        for (String table : tables) {
            jdbc.queryForList("select * from users." + table).forEach(row -> rows.add(table + " " + row));
        }
        return String.join("\n", rows);
    }

    protected static FeignException orderServiceDown() {
        Request request = Request.create(Request.HttpMethod.POST, "http://order-service/internal/privacy/customers",
                Map.of(), null, StandardCharsets.UTF_8, null);
        return new FeignException.ServiceUnavailable("order-service unavailable", request, null, Map.of());
    }

    private String passwordHash() {
        if (passwordHash == null) {
            passwordHash = passwordEncoder.encode(PASSWORD);
        }
        return passwordHash;
    }
}
