package com.jansetu.modules.reporting.api;

import com.jansetu.common.api.ApiResponse;
import com.jansetu.modules.reporting.service.ReportService;
import com.jansetu.modules.reporting.service.ReportService.DailyPoint;
import com.jansetu.modules.reporting.service.ReportService.DepartmentRow;
import com.jansetu.modules.reporting.service.ReportService.GrievanceTrends;
import com.jansetu.modules.reporting.service.ReportService.Summary;
import com.jansetu.modules.reporting.service.ReportService.WorkloadRow;
import com.jansetu.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports", description = "Aggregate metrics (REPORT:READ_DEPARTMENT scoped, REPORT:READ_ALL platform-wide)")
@PreAuthorize("hasAnyAuthority('REPORT:READ_DEPARTMENT', 'REPORT:READ_ALL')")
public class ReportController {

    private final ReportService reports;
    private final CurrentUser currentUser;

    public ReportController(ReportService reports, CurrentUser currentUser) {
        this.reports = reports;
        this.currentUser = currentUser;
    }

    @GetMapping("/summary")
    @Operation(summary = "Headline metrics: status distribution, throughput, SLA compliance, overdue work, grievances")
    public ApiResponse<Summary> summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                        @RequestParam(required = false) UUID departmentId) {
        return ApiResponse.of(reports.summary(from, to, departmentId, currentUser.require()));
    }

    @GetMapping("/application-volume")
    @Operation(summary = "Daily submitted and decided applications")
    public ApiResponse<List<DailyPoint>> volume(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                                @RequestParam(required = false) UUID departmentId) {
        return ApiResponse.of(reports.applicationVolume(from, to, departmentId, currentUser.require()));
    }

    @GetMapping("/department-performance")
    @Operation(summary = "Per-department throughput, SLA compliance and backlog")
    public ApiResponse<List<DepartmentRow>> departments(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ApiResponse.of(reports.departmentPerformance(from, to, currentUser.require()));
    }

    @GetMapping("/workload")
    @Operation(summary = "Active and overdue work per staff member")
    public ApiResponse<List<WorkloadRow>> workload(@RequestParam(required = false) UUID departmentId) {
        return ApiResponse.of(reports.workload(departmentId, currentUser.require()));
    }

    @GetMapping("/grievance-trends")
    @Operation(summary = "Daily grievances opened/resolved and counts by category")
    public ApiResponse<GrievanceTrends> grievances(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                                   @RequestParam(required = false) UUID departmentId) {
        return ApiResponse.of(reports.grievanceTrends(from, to, departmentId, currentUser.require()));
    }
}
