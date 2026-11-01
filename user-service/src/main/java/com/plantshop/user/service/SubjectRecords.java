package com.plantshop.user.service;

import com.plantshop.user.dto.ConsentResponse;
import com.plantshop.user.dto.PersonalDataExport.Consents;
import com.plantshop.user.dto.PersonalDataExport.Sections;
import com.plantshop.user.dto.PersonalDataExport.Subject;
import com.plantshop.user.entity.Address;
import com.plantshop.user.entity.AnalyticsEvent;
import com.plantshop.user.entity.ConsentHistoryEntry;
import com.plantshop.user.entity.ConsentPurpose;
import com.plantshop.user.entity.EmailMessage;
import com.plantshop.user.entity.PrivacyAuditEvent;
import com.plantshop.user.entity.User;
import com.plantshop.user.entity.UserActivity;
import com.plantshop.user.entity.UserConsent;
import com.plantshop.user.entity.UserSettings;
import com.plantshop.user.mapper.ConsentMapper;
import com.plantshop.user.mapper.PersonalDataMapper;
import com.plantshop.user.privacy.OrderHistory;
import com.plantshop.user.privacy.PrivacyProperties;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * One consistent snapshot of everything user-service stores about a customer, read in one transaction, and its
 * presentation as export sections. The lists are copied, so the snapshot cannot change after it was read.
 *
 * @param settings null when the customer never saved any
 */
record SubjectRecords(User user, List<Address> addresses, UserSettings settings, List<UserActivity> activity,
                      List<UserConsent> consents, List<ConsentHistoryEntry> consentHistory,
                      List<EmailMessage> communications, List<AnalyticsEvent> analytics,
                      List<PrivacyAuditEvent> privacyRequests) {

    SubjectRecords {
        Objects.requireNonNull(user, "user");
        addresses = List.copyOf(addresses);
        activity = List.copyOf(activity);
        consents = List.copyOf(consents);
        consentHistory = List.copyOf(consentHistory);
        communications = List.copyOf(communications);
        analytics = List.copyOf(analytics);
        privacyRequests = List.copyOf(privacyRequests);
    }

    Sections toSections(OrderHistory orders, PrivacyProperties properties) {
        return new Sections(
                PersonalDataMapper.toProfile(user),
                addresses.stream().map(PersonalDataMapper::toEntry).toList(),
                settings == null ? null : PersonalDataMapper.toSettings(settings),
                activity.stream().map(PersonalDataMapper::toEntry).toList(),
                new Consents(currentConsents(properties), consentHistory.stream().map(ConsentMapper::toResponse).toList()),
                communications.stream().map(PersonalDataMapper::toEntry).toList(),
                analytics.stream().map(PersonalDataMapper::toEntry).toList(),
                orders.entries(),
                privacyRequests.stream().map(PersonalDataMapper::toEntry).toList());
    }

    Subject subject() {
        return new Subject(user.getId(), user.getStatus().name(), user.getCreatedAt());
    }

    /** Every purpose, including those the customer never decided on. */
    private List<ConsentResponse> currentConsents(PrivacyProperties properties) {
        final Map<ConsentPurpose, UserConsent> byPurpose = consents.stream()
                .collect(Collectors.toMap(UserConsent::getPurpose, Function.identity()));
        return Arrays.stream(ConsentPurpose.values())
                .map(purpose -> ConsentMapper.toResponse(purpose, byPurpose.get(purpose),
                        properties.currentPolicyVersion(purpose), null))
                .toList();
    }
}
