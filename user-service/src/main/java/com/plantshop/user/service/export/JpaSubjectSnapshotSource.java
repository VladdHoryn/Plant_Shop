package com.plantshop.user.service.export;

import com.plantshop.user.repository.AddressRepository;
import com.plantshop.user.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Transaction boundary of the snapshot: reads the account and asks the two other local sources for the rest, all
 * inside one read-only transaction, so the export never mixes two states of the database.
 */
@Component
public class JpaSubjectSnapshotSource implements SubjectSnapshotSource {

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final CustomerChoicesSource choicesSource;
    private final ActivityTraceSource traceSource;

    public JpaSubjectSnapshotSource(final UserRepository userRepository, final AddressRepository addressRepository,
                                    final CustomerChoicesSource choicesSource, final ActivityTraceSource traceSource) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.choicesSource = choicesSource;
        this.traceSource = traceSource;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SubjectRecords> load(final Long subjectId) {
        return userRepository.findActiveById(subjectId).map(user -> new SubjectRecords(user,
                addressRepository.findByUserIdOrderById(subjectId),
                choicesSource.read(subjectId),
                traceSource.read(subjectId)));
    }
}
