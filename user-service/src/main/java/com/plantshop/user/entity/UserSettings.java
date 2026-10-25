package com.plantshop.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "user_settings")
@Getter
@Setter
@NoArgsConstructor
public class UserSettings {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 5)
    private String language;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "dark_mode", nullable = false)
    private boolean darkMode;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static UserSettings defaults(Long userId) {
        UserSettings settings = new UserSettings();
        settings.setUserId(userId);
        settings.setLanguage("uk");
        settings.setCurrency("UAH");
        return settings;
    }
}
