package com.jansetu.modules.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@IdClass(NotificationPreference.Key.class)
@Table(name = "notification_preferences")
public class NotificationPreference {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(length = 20)
    private String channel;

    @Id
    @Column(name = "event_category", length = 50)
    private String eventCategory;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationPreference() {
    }

    public NotificationPreference(UUID userId, String channel, String eventCategory, boolean enabled, Instant now) {
        this.userId = userId;
        this.channel = channel;
        this.eventCategory = eventCategory;
        this.enabled = enabled;
        this.updatedAt = now;
    }

    public void setEnabled(boolean enabled, Instant now) {
        this.enabled = enabled;
        this.updatedAt = now;
    }

    public String getChannel() {
        return channel;
    }

    public String getEventCategory() {
        return eventCategory;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public static class Key implements Serializable {
        private UUID userId;
        private String channel;
        private String eventCategory;

        public Key() {
        }

        public Key(UUID userId, String channel, String eventCategory) {
            this.userId = userId;
            this.channel = channel;
            this.eventCategory = eventCategory;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(userId, k.userId) && Objects.equals(channel, k.channel)
                    && Objects.equals(eventCategory, k.eventCategory);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, channel, eventCategory);
        }
    }
}
