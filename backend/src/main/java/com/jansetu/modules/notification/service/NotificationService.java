package com.jansetu.modules.notification.service;

import com.jansetu.common.api.PageQuery;
import com.jansetu.common.error.ApiException;
import com.jansetu.modules.notification.domain.Notification;
import com.jansetu.modules.notification.domain.NotificationPreference;
import com.jansetu.modules.notification.repository.NotificationPreferenceRepository;
import com.jansetu.modules.notification.repository.NotificationRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * In-app notifications. Business modules create notifications inside their own transaction, so a notification
 * exists only if the business change committed. E-mail delivery via an outbox worker is a later phase; the e-mail
 * preference is stored now so users can opt out in advance.
 */
@Service
public class NotificationService {

    public static final Set<String> CHANNELS = Set.of("IN_APP", "EMAIL");
    public static final List<String> CATEGORIES = List.of("APPLICATION", "GRIEVANCE", "TASK", "ACCOUNT");

    public record PreferenceView(String channel, String category, boolean enabled) {
    }

    private final NotificationRepository repository;
    private final NotificationPreferenceRepository preferences;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, NotificationPreferenceRepository preferences,
                               Clock clock) {
        this.repository = repository;
        this.preferences = preferences;
        this.clock = clock;
    }

    /** Creates an in-app notification unless the user disabled this category. Never put sensitive data in text. */
    @Transactional
    public void notify(UUID userId, String category, String type, String title, String body, String resourceType,
                       UUID resourceId) {
        if (userId == null) {
            return;
        }
        boolean enabled = preferences.findById(new NotificationPreference.Key(userId, "IN_APP", category))
                .map(NotificationPreference::isEnabled).orElse(true);
        if (enabled) {
            repository.save(new Notification(userId, type, title, body, resourceType, resourceId, clock.instant()));
        }
    }

    @Transactional(readOnly = true)
    public Page<Notification> list(UUID userId, boolean unreadOnly, PageQuery pageQuery) {
        Pageable pageable = pageQuery.toPageable(Map.of("createdAt", "createdAt"), Sort.by(Sort.Direction.DESC, "createdAt"));
        return unreadOnly ? repository.findByUserIdAndReadAtIsNull(userId, pageable) : repository.findByUserId(userId, pageable);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return repository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public Notification markRead(UUID id, UUID userId) {
        Notification notification = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Notification"));
        notification.markRead(clock.instant());
        return notification;
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return repository.markAllRead(userId, clock.instant());
    }

    @Transactional(readOnly = true)
    public List<PreferenceView> preferences(UUID userId) {
        Map<String, Boolean> stored = preferences.findByUserId(userId).stream()
                .collect(Collectors.toMap(p -> p.getChannel() + "|" + p.getEventCategory(), NotificationPreference::isEnabled));
        List<PreferenceView> result = new ArrayList<>();
        for (String channel : List.of("IN_APP", "EMAIL")) {
            for (String category : CATEGORIES) {
                result.add(new PreferenceView(channel, category, stored.getOrDefault(channel + "|" + category, true)));
            }
        }
        return result;
    }

    @Transactional
    public List<PreferenceView> updatePreferences(UUID userId, List<PreferenceView> updates) {
        Map<NotificationPreference.Key, NotificationPreference> existing = preferences.findByUserId(userId).stream()
                .collect(Collectors.toMap(p -> new NotificationPreference.Key(userId, p.getChannel(), p.getEventCategory()),
                        Function.identity()));
        for (PreferenceView update : updates) {
            if (!CHANNELS.contains(update.channel()) || !CATEGORIES.contains(update.category())) {
                throw ApiException.businessRule("Unknown notification channel or category.");
            }
            var key = new NotificationPreference.Key(userId, update.channel(), update.category());
            NotificationPreference pref = existing.get(key);
            if (pref == null) {
                preferences.save(new NotificationPreference(userId, update.channel(), update.category(),
                        update.enabled(), clock.instant()));
            } else {
                pref.setEnabled(update.enabled(), clock.instant());
            }
        }
        preferences.flush();
        return preferences(userId);
    }
}
