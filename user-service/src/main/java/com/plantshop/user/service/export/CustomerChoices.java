package com.plantshop.user.service.export;

import com.plantshop.user.entity.ConsentHistoryEntry;
import com.plantshop.user.entity.UserConsent;
import com.plantshop.user.entity.UserSettings;

import java.util.List;

/**
 * What the customer decided: saved preferences, current consents and the consent history.
 * Immutable: the lists are copied when the value is created.
 *
 * @param settings null when the customer never saved any
 */
public record CustomerChoices(UserSettings settings, List<UserConsent> consents,
                              List<ConsentHistoryEntry> consentHistory) {

    public CustomerChoices {
        consents = List.copyOf(consents);
        consentHistory = List.copyOf(consentHistory);
    }
}
