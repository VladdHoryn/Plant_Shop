package com.plantshop.user.repository;

import com.plantshop.user.entity.ConsentPurpose;
import com.plantshop.user.entity.ConsentStatus;
import com.plantshop.user.entity.UserConsent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {

    Optional<UserConsent> findByUserIdAndPurpose(Long userId, ConsentPurpose purpose);

    List<UserConsent> findByUserIdOrderByPurpose(Long userId);

    List<UserConsent> findByUserIdAndStatus(Long userId, ConsentStatus status);

    long countByUserId(Long userId);
}
