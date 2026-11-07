package com.plantshop.user.service.export;

import com.plantshop.user.dto.PersonalDataExport;
import com.plantshop.user.privacy.OrderHistory;

/** Port: turns the local snapshot and the remote order history into the versioned export document. */
public interface ExportAssembler {

    PersonalDataExport assemble(String requestedBy, SubjectRecords records, OrderHistory orders);
}
