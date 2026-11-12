package com.jansetu.modules.notification.api;

import com.jansetu.common.api.ApiResponse;
import com.jansetu.common.api.PageQuery;
import com.jansetu.common.api.PageResponse;
import com.jansetu.modules.notification.domain.Notification;
import com.jansetu.modules.notification.service.NotificationService;
import com.jansetu.modules.notification.service.NotificationService.PreferenceView;
import com.jansetu.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "In-app notifications of the signed-in user")
public class NotificationController {

    public record NotificationResponse(UUID id, String type, String title, String body, String resourceType,
                                       UUID resourceId, Instant readAt, Instant createdAt) {
        static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getResourceType(),
                    n.getResourceId(), n.getReadAt(), n.getCreatedAt());
        }
    }

    public record PreferenceItem(@NotBlank String channel, @NotBlank String category, @NotNull Boolean enabled) {
    }

    public record UpdatePreferencesRequest(@NotNull @Size(max = 20) List<@Valid PreferenceItem> preferences) {
    }

    private final NotificationService service;
    private final CurrentUser currentUser;

    public NotificationController(NotificationService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(summary = "List my notifications (newest first)")
    public ApiResponse<PageResponse<NotificationResponse>> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                                                @ParameterObject PageQuery page) {
        return ApiResponse.of(PageResponse.from(service.list(currentUser.require().id(), unreadOnly, page),
                NotificationResponse::from));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Number of unread notifications")
    public ApiResponse<Map<String, Long>> unreadCount() {
        return ApiResponse.of(Map.of("unread", service.unreadCount(currentUser.require().id())));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark one notification as read")
    public ApiResponse<NotificationResponse> markRead(@PathVariable UUID id) {
        return ApiResponse.of(NotificationResponse.from(service.markRead(id, currentUser.require().id())));
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark all my notifications as read")
    public ApiResponse<Map<String, Integer>> markAllRead() {
        return ApiResponse.of(Map.of("updated", service.markAllRead(currentUser.require().id())));
    }

    @GetMapping("/preferences")
    @Operation(summary = "My notification preferences (channel x category)")
    public ApiResponse<List<PreferenceView>> preferences() {
        return ApiResponse.of(service.preferences(currentUser.require().id()));
    }

    @PutMapping("/preferences")
    @Operation(summary = "Update my notification preferences")
    public ApiResponse<List<PreferenceView>> updatePreferences(@Valid @RequestBody UpdatePreferencesRequest request) {
        return ApiResponse.of(service.updatePreferences(currentUser.require().id(), request.preferences().stream()
                .map(p -> new PreferenceView(p.channel(), p.category(), p.enabled())).toList()));
    }
}
