package com.plantshop.user.privacy;

import com.plantshop.user.entity.ConsentPurpose;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * @param pseudonymizationKey key of the HMAC that turns user ids into log/audit pseudonyms
 * @param policyVersions      current text version of each consent purpose
 */
@ConfigurationProperties("privacy")
public record PrivacyProperties(String pseudonymizationKey, Map<ConsentPurpose, String> policyVersions) {

    public String currentPolicyVersion(ConsentPurpose purpose) {
        return policyVersions.get(purpose);
    }
}
