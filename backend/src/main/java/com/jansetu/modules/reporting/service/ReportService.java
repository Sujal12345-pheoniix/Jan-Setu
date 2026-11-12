package com.jansetu.modules.reporting.service;

import com.jansetu.common.error.ApiException;
import com.jansetu.common.error.ErrorCode;
import com.jansetu.security.AuthenticatedUser;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregate operational reports (read models over the case tables). Results contain counts, rates and durations
 * only, never citizen personal data. Scope: REPORT:READ_ALL sees every department; REPORT:READ_DEPARTMENT only the
 * actor's departments. Date ranges are UTC, inclusive of {@code from} and exclusive of {@code to}, at most 366 days.
 *
 * <p>Metric definitions:
 * <ul>
 *   <li><b>submitted</b>: applications whose submitted_at falls in the range;</li>
 *   <li><b>decided</b>: applications approved or rejected (decided_at) in the range;</li>
 *   <li><b>SLA compliance</b>: share of decided applications decided within the service's processing days
 *       (calendar days, measured from submission; clarification waiting time is not excluded here);</li>
 *   <li><b>average processing days</b>: mean submitted→decided duration of decided applications;</li>
 *   <li><b>overdue tasks</b>: active, non-blocked tasks whose due date has passed (as of now).</li>
 * </ul>
 */
@Service
public class ReportService {

    public record Range(Instant from, Instant to) {
    }

    public record Summary(Range range, Map<String, Long> applicationsByStatus, long submitted, long decided,
                          long approved, long rejected, Double slaCompliancePct, Double avgProcessingDays,
                          long activeTasks, long overdueTasks, Map<String, Long> grievancesByStatus,
                          long openGrievances) {
    }

    public record DailyPoint(LocalDate date, long submitted, long decided) {
    }

    public record DepartmentRow(UUID departmentId, String code, String name, long submitted, long approved,
                                long rejected, long pending, long overdueTasks, Double slaCompliancePct,
                                Double avgProcessingDays, long openGrievances) {
    }

    public record WorkloadRow(UUID assigneeId, String assigneeName, long activeTasks, long overdueTasks,
                              long openGrievances) {
    }

    public record GrievancePoint(LocalDate date, long opened, long resolved) {
    }

    public record GrievanceTrends(List<GrievancePoint> daily, Map<String, Long> byCategory) {
    }

    private static final Set<String> OPEN_GRIEVANCE = Set.of("SUBMITTED", "ACKNOWLEDGED", "ASSIGNED", "IN_PROGRESS", "REOPENED");

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public ReportService(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Summary summary(Instant from, Instant to, UUID departmentId, AuthenticatedUser actor) {
        Range range = range(from, to);
        MapSqlParameterSource p = scope(actor, departmentId, range);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        jdbc.query("select status, count(*) c from applications a where status <> 'DRAFT' and " + SCOPE + " group by status order by status",
                p, rs -> { byStatus.put(rs.getString("status"), rs.getLong("c")); });
        long submitted = count("select count(*) from applications a where submitted_at >= :from and submitted_at < :to and " + SCOPE, p);
        Map<String, Object> decided = jdbc.queryForMap("""
                select count(*) filter (where a.status in ('APPROVED','COMPLETED')) approved,
                       count(*) filter (where a.status = 'REJECTED') rejected,
                       count(*) decided,
                       avg(extract(epoch from (a.decided_at - a.submitted_at)) / 86400.0) avg_days,
                       100.0 * count(*) filter (where a.decided_at <= a.submitted_at + make_interval(days => sv.processing_days))
                             / nullif(count(*), 0) sla
                from applications a
                join service_versions sv on sv.id = a.service_version_id
                where a.decided_at >= :from and a.decided_at < :to and\s""" + SCOPE, p);
        Map<String, Object> tasks = jdbc.queryForMap("""
                select count(*) active,
                       count(*) filter (where due_at < :now and status <> 'BLOCKED') overdue
                from workflow_tasks a where status in ('OPEN','ASSIGNED','IN_PROGRESS','BLOCKED') and\s""" + SCOPE, p);
        Map<String, Long> grievances = new LinkedHashMap<>();
        jdbc.query("select status, count(*) c from grievances a where " + SCOPE + " group by status order by status",
                p, rs -> { grievances.put(rs.getString("status"), rs.getLong("c")); });
        long open = grievances.entrySet().stream().filter(e -> OPEN_GRIEVANCE.contains(e.getKey()))
                .mapToLong(Map.Entry::getValue).sum();
        return new Summary(range, byStatus, submitted, num(decided.get("decided")), num(decided.get("approved")),
                num(decided.get("rejected")), dbl(decided.get("sla")), dbl(decided.get("avg_days")),
                num(tasks.get("active")), num(tasks.get("overdue")), grievances, open);
    }

    @Transactional(readOnly = true)
    public List<DailyPoint> applicationVolume(Instant from, Instant to, UUID departmentId, AuthenticatedUser actor) {
        Range range = range(from, to);
        MapSqlParameterSource p = scope(actor, departmentId, range);
        return jdbc.query("""
                select d::date bucket,
                       (select count(*) from applications a where a.submitted_at >= d and a.submitted_at < d + interval '1 day' and\s""" + SCOPE + """
                       ) submitted,
                       (select count(*) from applications a where a.decided_at >= d and a.decided_at < d + interval '1 day' and\s""" + SCOPE + """
                       ) decided
                from generate_series(date_trunc('day', cast(:from as timestamptz) at time zone 'UTC') at time zone 'UTC',
                                     cast(:to as timestamptz) - interval '1 second', interval '1 day') d
                order by d""", p,
                (rs, i) -> new DailyPoint(rs.getObject("bucket", LocalDate.class), rs.getLong("submitted"), rs.getLong("decided")));
    }

    @Transactional(readOnly = true)
    public List<DepartmentRow> departmentPerformance(Instant from, Instant to, AuthenticatedUser actor) {
        Range range = range(from, to);
        MapSqlParameterSource p = scope(actor, null, range);
        return jdbc.query("""
                select dep.id, dep.code, dep.name,
                  (select count(*) from applications a where a.department_id = dep.id and a.submitted_at >= :from and a.submitted_at < :to) submitted,
                  (select count(*) from applications a where a.department_id = dep.id and a.decided_at >= :from and a.decided_at < :to
                        and a.status in ('APPROVED','COMPLETED')) approved,
                  (select count(*) from applications a where a.department_id = dep.id and a.decided_at >= :from and a.decided_at < :to
                        and a.status = 'REJECTED') rejected,
                  (select count(*) from applications a where a.department_id = dep.id
                        and a.status in ('SUBMITTED','UNDER_REVIEW','CLARIFICATION_REQUIRED','RESUBMITTED','PENDING_APPROVAL')) pending,
                  (select count(*) from workflow_tasks t where t.department_id = dep.id and t.status in ('OPEN','ASSIGNED','IN_PROGRESS')
                        and t.due_at < :now) overdue,
                  (select 100.0 * count(*) filter (where a.decided_at <= a.submitted_at + make_interval(days => sv.processing_days)) / nullif(count(*), 0)
                     from applications a join service_versions sv on sv.id = a.service_version_id
                     where a.department_id = dep.id and a.decided_at >= :from and a.decided_at < :to) sla,
                  (select avg(extract(epoch from (a.decided_at - a.submitted_at)) / 86400.0) from applications a
                     where a.department_id = dep.id and a.decided_at >= :from and a.decided_at < :to) avg_days,
                  (select count(*) from grievances g where g.department_id = dep.id
                        and g.status in ('SUBMITTED','ACKNOWLEDGED','ASSIGNED','IN_PROGRESS','REOPENED')) open_grievances
                from departments dep
                where (:all or dep.id in (:ids))
                order by dep.name""", p,
                (rs, i) -> new DepartmentRow(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
                        rs.getLong("submitted"), rs.getLong("approved"), rs.getLong("rejected"), rs.getLong("pending"),
                        rs.getLong("overdue"), dbl(rs.getObject("sla")), dbl(rs.getObject("avg_days")),
                        rs.getLong("open_grievances")));
    }

    /** Workload per staff member (staff names only; no citizen data). */
    @Transactional(readOnly = true)
    public List<WorkloadRow> workload(UUID departmentId, AuthenticatedUser actor) {
        MapSqlParameterSource p = scope(actor, departmentId, range(null, null));
        return jdbc.query("""
                select coalesce(t.assignee_id, g.assignee_id) assignee_id, u.full_name,
                       coalesce(t.active, 0) active, coalesce(t.overdue, 0) overdue, coalesce(g.open, 0) open_grievances
                from (select assignee_id, count(*) active,
                             count(*) filter (where due_at < :now and status <> 'BLOCKED') overdue
                      from workflow_tasks a where status in ('OPEN','ASSIGNED','IN_PROGRESS','BLOCKED') and\s""" + SCOPE + """
                      group by assignee_id) t
                full outer join (select assignee_id, count(*) open from grievances a
                      where status in ('ASSIGNED','IN_PROGRESS','REOPENED') and\s""" + SCOPE + """
                      group by assignee_id) g on g.assignee_id = t.assignee_id
                left join users u on u.id = coalesce(t.assignee_id, g.assignee_id)
                order by active desc nulls last""", p,
                (rs, i) -> new WorkloadRow(rs.getObject("assignee_id", UUID.class),
                        rs.getString("full_name") == null ? "Unassigned" : rs.getString("full_name"),
                        rs.getLong("active"), rs.getLong("overdue"), rs.getLong("open_grievances")));
    }

    @Transactional(readOnly = true)
    public GrievanceTrends grievanceTrends(Instant from, Instant to, UUID departmentId, AuthenticatedUser actor) {
        Range range = range(from, to);
        MapSqlParameterSource p = scope(actor, departmentId, range);
        List<GrievancePoint> daily = jdbc.query("""
                select d::date bucket,
                       (select count(*) from grievances a where a.created_at >= d and a.created_at < d + interval '1 day' and\s""" + SCOPE + """
                       ) opened,
                       (select count(*) from grievances a where a.resolved_at >= d and a.resolved_at < d + interval '1 day' and\s""" + SCOPE + """
                       ) resolved
                from generate_series(date_trunc('day', cast(:from as timestamptz) at time zone 'UTC') at time zone 'UTC',
                                     cast(:to as timestamptz) - interval '1 second', interval '1 day') d
                order by d""", p,
                (rs, i) -> new GrievancePoint(rs.getObject("bucket", LocalDate.class), rs.getLong("opened"), rs.getLong("resolved")));
        Map<String, Long> byCategory = new LinkedHashMap<>();
        jdbc.query("select category, count(*) c from grievances a where created_at >= :from and created_at < :to and "
                + SCOPE + " group by category order by c desc", p, rs -> { byCategory.put(rs.getString("category"), rs.getLong("c")); });
        return new GrievanceTrends(daily, byCategory);
    }

    // ---- scope and helpers -----------------------------------------------------------------------------------

    private static final String SCOPE = "(:all or a.department_id in (:ids))";

    private MapSqlParameterSource scope(AuthenticatedUser actor, UUID departmentId, Range range) {
        boolean all = actor.hasPermission("REPORT:READ_ALL") && actor.hasGlobalScope();
        if (!all && !actor.hasPermission("REPORT:READ_DEPARTMENT")) {
            throw ApiException.accessDenied("You do not have permission to view reports.");
        }
        List<UUID> ids;
        if (departmentId != null) {
            if (!all && !actor.departmentIds().contains(departmentId)) {
                throw ApiException.notFound("Department");
            }
            ids = List.of(departmentId);
            all = false;
        } else if (all) {
            ids = List.of(new UUID(0, 0));
        } else {
            ids = actor.departmentIds().isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(actor.departmentIds());
        }
        return new MapSqlParameterSource()
                .addValue("all", all)
                .addValue("ids", ids)
                .addValue("from", Timestamp.from(range.from()))
                .addValue("to", Timestamp.from(range.to()))
                .addValue("now", Timestamp.from(clock.instant()));
    }

    private Range range(Instant from, Instant to) {
        Instant end = to == null ? LocalDate.now(clock).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC) : to;
        Instant start = from == null ? end.minus(Duration.ofDays(30)) : from;
        if (!start.isBefore(end)) {
            throw new ApiException(ErrorCode.INVALID_PARAMETER, "'from' must be before 'to'.");
        }
        if (Duration.between(start, end).toDays() > 366) {
            throw new ApiException(ErrorCode.INVALID_PARAMETER, "The date range can be at most 366 days.");
        }
        return new Range(start, end);
    }

    private long count(String sql, MapSqlParameterSource p) {
        Long value = jdbc.queryForObject(sql, p, Long.class);
        return value == null ? 0 : value;
    }

    private static long num(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    private static Double dbl(Object value) {
        return value == null ? null : Math.round(((Number) value).doubleValue() * 10) / 10.0;
    }
}
