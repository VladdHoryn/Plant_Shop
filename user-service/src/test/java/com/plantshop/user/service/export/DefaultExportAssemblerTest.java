package com.plantshop.user.service.export;

import com.plantshop.user.dto.PersonalDataExport;
import com.plantshop.user.entity.ConsentPurpose;
import com.plantshop.user.entity.User;
import com.plantshop.user.entity.UserRole;
import com.plantshop.user.privacy.ExportOmissions;
import com.plantshop.user.privacy.OrderHistory;
import com.plantshop.user.privacy.PrivacyProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.plantshop.user.privacy.DataCategory.CONTACT_DATA;
import static com.plantshop.user.privacy.DataCategory.DIRECT_IDENTIFIERS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The assembly of the export document. Since Lab 8 it is a separate, pure component, so the Lab 7 regression test
 * (categories of an empty section must not erase those of a section with data) needs neither mocks nor a database.
 */
class DefaultExportAssemblerTest {

    private final DefaultExportAssembler assembler = new DefaultExportAssembler(new PrivacyProperties("test-key",
            Map.of(ConsentPurpose.MARKETING_EMAIL, "v1", ConsentPurpose.OPTIONAL_ANALYTICS, "v1")));

    @Test
    void categoriesOfAnEmptySectionDoNotEraseThoseOfASectionWithData() {
        final PersonalDataExport export = assembler.assemble("SUBJECT", profileOnly(),
                OrderHistory.available(List.of()));

        assertThat(export.sections().profile().email()).isEqualTo("profile.only@example.test");
        assertThat(export.sections().profile().phone()).isEqualTo("+380501112233");
        assertThat(export.sections().addresses()).isEmpty();
        assertThat(export.categories())
                .as("categories declared for a customer whose only data is the profile (e-mail, phone)")
                .containsExactly(DIRECT_IDENTIFIERS, CONTACT_DATA);
        assertThat(export.metadata().complete()).isTrue();
        assertThat(export.excludedFields()).isEqualTo(ExportOmissions.EXCLUDED_FIELDS);
    }

    @Test
    void anUnavailableOrderSourceMakesTheExportIncomplete() {
        final PersonalDataExport export = assembler.assemble("PRIVACY_OFFICER", profileOnly(),
                OrderHistory.UNAVAILABLE);

        assertThat(export.metadata().complete()).isFalse();
        assertThat(export.metadata().requestedBy()).isEqualTo("PRIVACY_OFFICER");
        assertThat(export.omissions()).extracting(PersonalDataExport.Omission::section).contains("orders");
    }

    private static SubjectRecords profileOnly() {
        final User user = new User();
        user.setId(42L);
        user.setFullName("Profile Only");
        user.setEmail("profile.only@example.test");
        user.setPhone("+380501112233");
        user.setRole(UserRole.CUSTOMER);
        user.setCreatedAt(Instant.parse("2026-10-01T10:00:00Z"));
        return new SubjectRecords(user, List.of(), new CustomerChoices(null, List.of(), List.of()),
                new ActivityTrace(List.of(), List.of(), List.of(), List.of()));
    }
}
