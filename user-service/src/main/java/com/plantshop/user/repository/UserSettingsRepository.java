package com.plantshop.user.repository;

import com.plantshop.user.entity.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {

    @Modifying
    @Query("delete from UserSettings s where s.userId = :userId")
    int deleteByUserId(Long userId);
}
