package com.jansetu.modules.application.api;

import com.jansetu.common.api.ApiResponse;
import com.jansetu.common.api.PageQuery;
import com.jansetu.common.api.PageResponse;
import com.jansetu.modules.application.service.CaseworkService;
import com.jansetu.modules.application.service.CaseworkService.TaskFilter;
import com.jansetu.modules.application.service.CaseworkService.TaskQueueItem;
import com.jansetu.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tasks")
@Tag(name = "Tasks", description = "Department work queues (TASK:READ / TASK:CLAIM / TASK:ASSIGN)")
public class TaskController {

    public record AssignRequest(@NotNull UUID assigneeId) {
    }

    private final CaseworkService casework;
    private final CurrentUser currentUser;

    public TaskController(CaseworkService casework, CurrentUser currentUser) {
        this.casework = casework;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(summary = "Active tasks in your departments", description = "Filters: mine, unassigned, step (REVIEW/APPROVAL), overdue. Sortable by dueAt, createdAt.")
    public ApiResponse<PageResponse<TaskQueueItem>> list(@RequestParam(defaultValue = "false") boolean mine,
                                                         @RequestParam(defaultValue = "false") boolean unassigned,
                                                         @RequestParam(required = false) String step,
                                                         @RequestParam(defaultValue = "false") boolean overdue,
                                                         @RequestParam(required = false) UUID departmentId,
                                                         @ParameterObject PageQuery page) {
        var filter = new TaskFilter(mine, unassigned, step, overdue, departmentId);
        return ApiResponse.of(PageResponse.of(casework.tasks(filter, page, currentUser.require())));
    }

    @PostMapping("/{id}/claim")
    @Operation(summary = "Claim a task (review tasks start the review)")
    public ApiResponse<Map<String, String>> claim(@PathVariable UUID id) {
        casework.claimTask(id, currentUser.require());
        return ApiResponse.of(Map.of("status", "CLAIMED"));
    }

    @PostMapping("/{id}/assign")
    @Operation(summary = "Assign or reassign a task to an eligible department member (TASK:ASSIGN)")
    public ApiResponse<Map<String, String>> assign(@PathVariable UUID id, @Valid @RequestBody AssignRequest request) {
        casework.assignTask(id, request.assigneeId(), currentUser.require());
        return ApiResponse.of(Map.of("status", "ASSIGNED"));
    }
}
