package com.plantshop.user.service;

import com.plantshop.user.dto.PersonalDataExport;
import com.plantshop.user.security.AppUserPrincipal;
import com.plantshop.user.service.export.ExportAccessGuard;
import com.plantshop.user.service.export.ExportAssembler;
import com.plantshop.user.service.export.OrderHistorySource;
import com.plantshop.user.service.export.SubjectSnapshotSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Right of access: everything the shop holds about one customer, in the versioned {@link PersonalDataExport}
 * format. Only coordinates four ports: access + audit, the local snapshot, the remote order history and
 * the assembly of the document. It knows none of their implementations, repositories or the Feign client.
 */
@Slf4j
@Service
public class PersonalDataExportService {

    private final ExportAccessGuard accessGuard;
    private final SubjectSnapshotSource snapshots;
    private final OrderHistorySource orderHistory;
    private final ExportAssembler assembler;

    public PersonalDataExportService(final ExportAccessGuard accessGuard, final SubjectSnapshotSource snapshots,
                                     final OrderHistorySource orderHistory, final ExportAssembler assembler) {
        this.accessGuard = accessGuard;
        this.snapshots = snapshots;
        this.orderHistory = orderHistory;
        this.assembler = assembler;
    }

    /**
     * The snapshot is read in its own transaction; the remote order call runs after it, outside any transaction.
     * Only the fact of the export is logged, never its content.
     */
    public PersonalDataExport export(final AppUserPrincipal actor, final Long subjectId) {
        final PersonalDataExport export = accessGuard.audited(actor, subjectId, request ->
                snapshots.load(request.subjectId()).map(records -> assembler.assemble(request.requestedBy(), records,
                        orderHistory.ordersOf(records.email()))));
        log.info("Personal data export generated exportId={} categories={} complete={}",
                export.metadata().exportId(), export.categories().size(), export.metadata().complete());
        return export;
    }
}
