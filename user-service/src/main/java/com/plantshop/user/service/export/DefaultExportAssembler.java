package com.plantshop.user.service.export;

import com.plantshop.user.dto.PersonalDataExport;
import com.plantshop.user.dto.PersonalDataExport.Metadata;
import com.plantshop.user.dto.PersonalDataExport.Sections;
import com.plantshop.user.privacy.ExportCoverage;
import com.plantshop.user.privacy.ExportOmissions;
import com.plantshop.user.privacy.ExportSection;
import com.plantshop.user.privacy.OrderHistory;
import com.plantshop.user.privacy.PrivacyProperties;
import org.springframework.stereotype.Component;

/**
 * Every intermediate result is an immutable value object: the sections, the omissions and the declared categories
 * are derived once and never changed afterwards. Pure: no I/O, no logging.
 */
@Component
public class DefaultExportAssembler implements ExportAssembler {

    private final PrivacyProperties properties;

    public DefaultExportAssembler(final PrivacyProperties properties) {
        this.properties = properties;
    }

    @Override
    public PersonalDataExport assemble(final String requestedBy, final SubjectRecords records,
                                       final OrderHistory orders) {
        final Sections sections = records.toSections(orders, properties);
        final ExportOmissions omissions = ExportOmissions.of(orders, sections);
        return new PersonalDataExport(Metadata.generated(requestedBy, omissions.complete()), records.subject(),
                ExportCoverage.of(sections).categories(), ExportSection.SOURCES, sections, omissions.items(),
                ExportOmissions.EXCLUDED_FIELDS);
    }
}
