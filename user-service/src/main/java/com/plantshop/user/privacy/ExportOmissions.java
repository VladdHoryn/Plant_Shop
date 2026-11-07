package com.plantshop.user.privacy;

import com.plantshop.user.dto.PersonalDataExport.ExcludedField;
import com.plantshop.user.dto.PersonalDataExport.Omission;
import com.plantshop.user.dto.PersonalDataExport.Sections;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * The sections an export could not fill, and why. Immutable: {@link #with} returns a new instance, so nothing
 * can be added to or removed from the omissions of an export once it has been assembled.
 */
public record ExportOmissions(List<Omission> items) {

    public static final String SOURCE_UNAVAILABLE = "SOURCE_UNAVAILABLE";
    /** Held about the customer but deliberately not exported (documented in the export itself). */
    public static final List<ExcludedField> EXCLUDED_FIELDS = List.of(
            new ExcludedField("profile.passwordHash", "SECURITY_CREDENTIAL"),
            new ExcludedField("profile.emailVerificationToken", "SECURITY_CREDENTIAL"),
            new ExcludedField("profile.passwordResetToken", "SECURITY_CREDENTIAL"),
            new ExcludedField("profile.fraudReviewFlag", "INTERNAL_SECURITY_CONTROL"),
            new ExcludedField("privacyRequests.actor", "OTHER_PERSONS_DATA"));
    public static final ExportOmissions NONE = new ExportOmissions(List.of());

    private static final Omission ORDERS_UNAVAILABLE = new Omission(ExportSection.ORDERS.key(),
            SOURCE_UNAVAILABLE + ": order-service did not answer, repeat the request later");
    private static final Omission SETTINGS_NOT_STORED = new Omission(ExportSection.SETTINGS.key(), "NO_DATA_STORED");

    public ExportOmissions {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
    }

    /** The orders come first because they are read first, then the settings. */
    public static ExportOmissions of(OrderHistory orders, Sections sections) {
        final ExportOmissions afterOrders = orders.sourceAvailable() ? NONE : NONE.with(ORDERS_UNAVAILABLE);
        return sections.settings() == null ? afterOrders.with(SETTINGS_NOT_STORED) : afterOrders;
    }

    public ExportOmissions with(Omission omission) {
        return new ExportOmissions(Stream.concat(items.stream(), Stream.of(Objects.requireNonNull(omission))).toList());
    }

    /** False when a source could not be read; a section that simply has no data does not make an export incomplete. */
    public boolean complete() {
        return items.stream().noneMatch(omission -> omission.reason().startsWith(SOURCE_UNAVAILABLE));
    }
}
