package com.jansetu.modules.notification.repository;

import com.jansetu.modules.notification.domain.NotificationPreference;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, NotificationPreference.Key> {

    List<NotificationPreference> findByUserId(UUID userId);
}
