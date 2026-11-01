package com.plantshop.user.privacy;

import com.plantshop.user.dto.PersonalDataExport.Omission;
import com.plantshop.user.dto.PersonalDataExport.OrderEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Lab 7, task 2: the value objects of the personal data export cannot be changed once created. */
class ExportValueObjectsTest {

    private static final Omission NO_SETTINGS = new Omission("settings", "NO_DATA_STORED");

    @Test
    void withReturnsANewInstanceAndLeavesTheOriginalUnchanged() {
        ExportOmissions none = ExportOmissions.NONE;

        ExportOmissions one = none.with(NO_SETTINGS);

        assertThat(one).isNotSameAs(none);
        assertThat(none.items()).isEmpty();
        assertThat(one.items()).containsExactly(NO_SETTINGS);
        assertThatThrownBy(() -> one.items().add(NO_SETTINGS)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void anUnreadSourceMakesTheExportIncompleteAMissingSectionDoesNot() {
        assertThat(ExportOmissions.NONE.with(NO_SETTINGS).complete()).isTrue();
        assertThat(ExportOmissions.NONE.with(new Omission("orders", ExportOmissions.SOURCE_UNAVAILABLE + ": down"))
                .complete()).isFalse();
    }

    @Test
    void coverageHasARuleForEverySectionAndYieldsAnUnmodifiableList() {
        ExportCoverage coverage = new ExportCoverage(Set.of(ExportSection.PROFILE, ExportSection.ADDRESSES));

        assertThat(ExportCoverage.coversEverySection()).isTrue();
        assertThat(coverage.categories()).containsExactly(DataCategory.DIRECT_IDENTIFIERS, DataCategory.CONTACT_DATA);
        assertThatThrownBy(() -> coverage.categories().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void anUnavailableOrderSourceCannotCarryOrders() {
        OrderEntry order = new OrderEntry(1L, "CONFIRMED", "CARD", BigDecimal.TEN, "Street 1",
                Instant.parse("2026-10-01T10:00:00Z"), List.of());

        assertThatThrownBy(() -> new OrderHistory(List.of(order), false)).isInstanceOf(IllegalArgumentException.class);
        assertThat(OrderHistory.available(List.of(order)).entries()).containsExactly(order);
    }
}
