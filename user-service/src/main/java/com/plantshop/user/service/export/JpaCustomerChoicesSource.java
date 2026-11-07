package com.plantshop.user.service.export;

import com.plantshop.user.repository.ConsentHistoryRepository;
import com.plantshop.user.repository.UserConsentRepository;
import com.plantshop.user.repository.UserSettingsRepository;
import org.springframework.stereotype.Component;

/** Preferences and consents from three tables. Joins the caller's transaction (see JpaSubjectSnapshotSource). */
@Component
public class JpaCustomerChoicesSource implements CustomerChoicesSource {

    private final UserSettingsRepository settingsRepository;
    private final UserConsentRepository consentRepository;
    private final ConsentHistoryRepository historyRepository;

    public JpaCustomerChoicesSource(final UserSettingsRepository settingsRepository,
                                    final UserConsentRepository consentRepository,
                                    final ConsentHistoryRepository historyRepository) {
        this.settingsRepository = settingsRepository;
        this.consentRepository = consentRepository;
        this.historyRepository = historyRepository;
    }

    @Override
    public CustomerChoices read(final Long subjectId) {
        return new CustomerChoices(
                settingsRepository.findById(subjectId).orElse(null),
                consentRepository.findByUserIdOrderByPurpose(subjectId),
                historyRepository.findByUserIdOrderById(subjectId));
    }
}
