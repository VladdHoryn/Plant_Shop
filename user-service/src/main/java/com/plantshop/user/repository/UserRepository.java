package com.plantshop.user.repository;

import com.plantshop.user.entity.User;
import com.plantshop.user.entity.UserStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByIdAndStatus(Long id, UserStatus status);

    /**
     * Row lock that serializes every privacy-relevant change of one user: consent changes, the consent
     * check before a dependent action, and erasure. A REVOKE therefore either completes before a
     * marketing send checks the gate, or waits until that send has finished.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(Long id);

    /** An erased (anonymized) account is treated like a missing one. */
    default Optional<User> findActiveById(Long id) {
        return findById(id).filter(User::isActive);
    }
}
