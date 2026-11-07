package com.plantshop.user.service;

import com.plantshop.user.dto.PersonalDataExport;
import com.plantshop.user.entity.User;
import com.plantshop.user.entity.UserRole;
import com.plantshop.user.exception.SubjectNotFoundException;
import com.plantshop.user.privacy.ExportRequest;
import com.plantshop.user.privacy.OrderHistory;
import com.plantshop.user.security.AppUserPrincipal;
import com.plantshop.user.service.export.ActivityTrace;
import com.plantshop.user.service.export.CustomerChoices;
import com.plantshop.user.service.export.ExportAccessGuard;
import com.plantshop.user.service.export.ExportAssembler;
import com.plantshop.user.service.export.OrderHistorySource;
import com.plantshop.user.service.export.SubjectRecords;
import com.plantshop.user.service.export.SubjectSnapshotSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Lab 8: the coordinator after the God Object decomposition. Every collaborator is a port (interface): the access
 * guard is a hand-written fake, the other three are Mockito mocks. No Spring context, no database, no Feign.
 */
@ExtendWith(MockitoExtension.class)
class PersonalDataExportServiceTest {

    private static final long SUBJECT_ID = 42L;

    @Mock
    private SubjectSnapshotSource snapshots;
    @Mock
    private OrderHistorySource orderHistory;
    @Mock
    private ExportAssembler assembler;
    private PersonalDataExportService service;

    /** Grants every request and turns an empty result into the same exception the real guard throws. */
    private static final class AllowingGuard implements ExportAccessGuard {
        @Override
        public <T> T audited(final AppUserPrincipal actor, final Long subjectId,
                             final Function<ExportRequest, Optional<T>> operation) {
            return operation.apply(new ExportRequest(actor, subjectId)).orElseThrow(SubjectNotFoundException::new);
        }
    }

    @BeforeEach
    void setUp() {
        service = new PersonalDataExportService(new AllowingGuard(), snapshots, orderHistory, assembler);
    }

    @Test
    void readsTheSnapshotThenTheOrdersThenAssemblesTheDocument() {
        final User user = customer();
        final SubjectRecords records = new SubjectRecords(user, List.of(),
                new CustomerChoices(null, List.of(), List.of()), new ActivityTrace(List.of(), List.of(), List.of(),
                List.of()));
        final OrderHistory orders = OrderHistory.available(List.of());
        final PersonalDataExport document = mock(PersonalDataExport.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        when(snapshots.load(SUBJECT_ID)).thenReturn(Optional.of(records));
        when(orderHistory.ordersOf("subject@example.test")).thenReturn(orders);
        when(assembler.assemble("SUBJECT", records, orders)).thenReturn(document);

        final PersonalDataExport export = service.export(new AppUserPrincipal(user), SUBJECT_ID);

        assertThat(export).isSameAs(document);
        final InOrder order = inOrder(snapshots, orderHistory, assembler);
        order.verify(snapshots).load(SUBJECT_ID);
        order.verify(orderHistory).ordersOf("subject@example.test");
        order.verify(assembler).assemble("SUBJECT", records, orders);
    }

    @Test
    void anUnknownSubjectNeverReachesOrderServiceOrTheAssembler() {
        when(snapshots.load(SUBJECT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.export(new AppUserPrincipal(customer()), SUBJECT_ID))
                .isInstanceOf(SubjectNotFoundException.class);
        verifyNoInteractions(orderHistory, assembler);
    }

    private static User customer() {
        final User user = new User();
        user.setId(SUBJECT_ID);
        user.setEmail("subject@example.test");
        user.setRole(UserRole.CUSTOMER);
        return user;
    }
}
