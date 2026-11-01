package com.plantshop.user.privacy;

import com.plantshop.user.dto.PersonalDataExport.Sections;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The export sections that hold data and, derived from them, the categories of personal data an export declares.
 * Immutable: the categories are computed from the whole set in one expression, so an empty section can never
 * take away a category that another section with data contributes (PROFILE and ADDRESSES both hold CONTACT_DATA).
 */
public record ExportCoverage(Set<ExportSection> sectionsWithData) {

    /** "Does this section hold data?" as data: one predicate per section instead of one branch per section. */
    private static final Map<ExportSection, Predicate<Sections>> HAS_DATA = Map.of(
            ExportSection.PROFILE, sections -> true,
            ExportSection.ADDRESSES, sections -> !sections.addresses().isEmpty(),
            ExportSection.SETTINGS, sections -> sections.settings() != null,
            ExportSection.ACTIVITY, sections -> !sections.activity().isEmpty(),
            ExportSection.CONSENTS, sections -> !sections.consents().history().isEmpty(),
            ExportSection.COMMUNICATIONS, sections -> !sections.communications().isEmpty(),
            ExportSection.ANALYTICS, sections -> !sections.analytics().isEmpty(),
            ExportSection.ORDERS, sections -> !sections.orders().isEmpty(),
            ExportSection.PRIVACY_REQUESTS, sections -> !sections.privacyRequests().isEmpty());

    public ExportCoverage {
        sectionsWithData = Set.copyOf(Objects.requireNonNull(sectionsWithData, "sectionsWithData"));
    }

    public static ExportCoverage of(Sections sections) {
        Objects.requireNonNull(sections, "sections");
        return new ExportCoverage(HAS_DATA.entrySet().stream()
                .filter(rule -> rule.getValue().test(sections))
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet()));
    }

    /** Every section of the export has a rule; a section without one would silently never be declared. */
    static boolean coversEverySection() {
        return HAS_DATA.keySet().containsAll(List.of(ExportSection.values()));
    }

    /** Every category of every section with data, each once, in the order of {@link DataCategory}. */
    public List<DataCategory> categories() {
        return sectionsWithData.stream()
                .flatMap(section -> section.categories().stream())
                .distinct()
                .sorted()
                .toList();
    }
}
