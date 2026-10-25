package com.plantshop.user.privacy;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Turns a user id into a stable pseudonym ({@code u_3fa9c2e1b04d7a65}) for logs and the audit trail.
 * This is pseudonymization, not anonymization: whoever holds the key can recompute the pseudonym for a
 * given id, which is exactly what an investigator needs and what a log reader must not be able to do.
 */
@Component
public class SubjectPseudonymizer {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public SubjectPseudonymizer(PrivacyProperties properties) {
        this.key = new SecretKeySpec(properties.pseudonymizationKey().getBytes(UTF_8), ALGORITHM);
    }

    public String ref(Long userId) {
        if (userId == null) {
            return "anonymous";
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] digest = mac.doFinal(("user:" + userId).getBytes(UTF_8));
            return "u_" + HexFormat.of().formatHex(digest, 0, 8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }
}
