package com.plantshop.user.service.export;

import com.plantshop.user.dto.ConsentResponse;
import com.plantshop.user.dto.PersonalDataExport.Consents;
import com.plantshop.user.dto.PersonalDataExport.Sections;
import com.plantshop.user.dto.PersonalDataExport.Subject;
import com.plantshop.user.entity.Address;
import com.plantshop.user.entity.ConsentPurpose;
import com.plantshop.user.entity.User;
import com.plantshop.user.entity.UserConsent;
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
 * One consistent snapshot of everything user-service stores about a customer, read in one transaction
 * ({@link JpaSubjectSnapshotSource}), and its presentation as export sections. Immutable: the parts are value
 * objects and the address list is copied, so the snapshot cannot change after it was read.
 */
public record SubjectRecords(User user, List<Address> addresses, CustomerChoices choices, ActivityTrace trace) {

    public SubjectRecords {
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(choices, "choices");
        Objects.requireNonNull(trace, "trace");
        addresses = List.copyOf(addresses);
    }

    public Sections toSections(OrderHistory orders, PrivacyProperties properties) {
        return new Sections(
                PersonalDataMapper.toProfile(user),
                addresses.stream().map(PersonalDataMapper::toEntry).toList(),
                choices.settings() == null ? null : PersonalDataMapper.toSettings(choices.settings()),
                trace.activity().stream().map(PersonalDataMapper::toEntry).toList(),
                new Consents(currentConsents(properties),
                        choices.consentHistory().stream().map(ConsentMapper::toResponse).toList()),
                trace.communications().stream().map(PersonalDataMapper::toEntry).toList(),
                trace.analytics().stream().map(PersonalDataMapper::toEntry).toList(),
                orders.entries(),
                trace.privacyRequests().stream().map(PersonalDataMapper::toEntry).toList());
    }

    public Subject subject() {
        return new Subject(user.getId(), user.getStatus().name(), user.getCreatedAt());
    }

    /** The key under which order-service links the customer's orders. */
    public String email() {
        return user.getEmail();
    }

    /** Every purpose, including those the customer never decided on. */
    private List<ConsentResponse> currentConsents(PrivacyProperties properties) {
        final Map<ConsentPurpose, UserConsent> byPurpose = choices.consents().stream()
                .collect(Collectors.toMap(UserConsent::getPurpose, Function.identity()));
        return Arrays.stream(ConsentPurpose.values())
                .map(purpose -> ConsentMapper.toResponse(purpose, byPurpose.get(purpose),
                        properties.currentPolicyVersion(purpose), null))
                .toList();
    }
}
