package com.plantshop.user.service.export;

import java.util.Optional;

/** Port: one consistent snapshot of the customer's data stored in user-service. */
public interface SubjectSnapshotSource {

    /** Empty when there is no active account with this id. */
    Optional<SubjectRecords> load(Long subjectId);
}
