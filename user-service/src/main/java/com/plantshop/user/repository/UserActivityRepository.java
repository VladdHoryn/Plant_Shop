package com.plantshop.user.repository;

import com.plantshop.user.entity.UserActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface UserActivityRepository extends JpaRepository<UserActivity, Long> {

    List<UserActivity> findByUserIdOrderByOccurredAtDesc(Long userId);

    @Modifying
    @Query("delete from UserActivity a where a.userId = :userId")
    int deleteByUserId(Long userId);
}
