package com.jansetu.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jansetu.modules.identity.domain.User;
import com.jansetu.modules.identity.domain.UserType;
import com.jansetu.modules.identity.repository.UserRepository;
import com.jansetu.support.IntegrationTestBase;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

/** Flyway migrations applied to a clean database, plus database-level integrity guarantees. */
class DatabaseSchemaIT extends IntegrationTestBase {

    @Autowired
    private Flyway flyway;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TransactionTemplate tx;

    @Test
    void allMigrationsAppliedSuccessfullyToCleanDatabase() {
        var applied = flyway.info().applied();
        assertThat(applied).hasSizeGreaterThanOrEqualTo(8);
        assertThat(applied).allSatisfy(m -> assertThat(m.getState().isApplied()).isTrue());
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void allPlannedTablesExist() {
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'", String.class);
        assertThat(tables).contains(
                "users", "roles", "permissions", "user_roles", "role_permissions", "departments",
                "department_memberships", "services", "service_requirements", "service_versions",
                "applications", "application_status_history", "application_assignments", "application_comments",
                "application_decisions", "clarification_requests", "documents", "document_versions",
                "workflow_definitions", "workflow_definition_versions", "workflow_steps", "workflow_instances",
                "workflow_tasks", "workflow_history", "grievances", "grievance_assignments", "grievance_comments",
                "grievance_feedback", "notifications", "notification_preferences", "audit_logs", "sla_policies",
                "sla_policy_versions", "sla_breaches", "refresh_sessions", "password_reset_tokens", "outbox_events",
                "idempotency_records");
    }

    @Test
    void referenceDataSeedsSevenSystemRoles() {
        List<String> roles = jdbc.queryForList("select code from roles where system_role order by code", String.class);
        assertThat(roles).containsExactly("AUDITOR", "CITIZEN", "DEPARTMENT_ADMIN", "MANAGEMENT_VIEWER", "OFFICER",
                "PLATFORM_ADMIN", "REVIEWER");
    }

    @Test
    void auditLogIsAppendOnlyAtDatabaseLevel() {
        jdbc.update("insert into audit_logs (occurred_at, actor_type, action, resource_type, outcome) "
                + "values (now(), 'SYSTEM', 'TEST_EVENT', 'TEST', 'SUCCESS')");
        assertThatThrownBy(() -> jdbc.update("update audit_logs set action = 'TAMPERED' where action = 'TEST_EVENT'"))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from audit_logs where action = 'TEST_EVENT'"))
                .hasMessageContaining("append-only");
    }

    @Test
    void normalizedEmailIsUniqueAtDatabaseLevel() {
        String email = "unique." + uniq() + "@example.test";
        userRepository.saveAndFlush(new User(email, "{noop}x", "First", null, UserType.CITIZEN, Instant.now(), false));
        assertThatThrownBy(() -> userRepository.saveAndFlush(
                new User(email.toUpperCase(), "{noop}x", "Second", null, UserType.CITIZEN, Instant.now(), false)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void checkConstraintsRejectInvalidStates() {
        assertThatThrownBy(() -> jdbc.update("insert into departments (code, name, status, created_at, updated_at) "
                + "values ('BAD_STATUS', 'Bad status dept', 'DELETED', now(), now())"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into departments (code, name, status, created_at, updated_at) "
                + "values ('lower', 'Lowercase code dept', 'ACTIVE', now(), now())"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void optimisticLockingPreventsLostUpdates() {
        UUID id = userRepository.saveAndFlush(new User("lock." + uniq() + "@example.test", "{noop}x", "Lock Test",
                null, UserType.CITIZEN, Instant.now(), false)).getId();
        User first = tx.execute(s -> userRepository.findById(id).orElseThrow());
        User second = tx.execute(s -> userRepository.findById(id).orElseThrow());
        first.updateProfile("Writer One", null);
        userRepository.saveAndFlush(first);
        second.updateProfile("Writer Two", null);
        assertThatThrownBy(() -> userRepository.saveAndFlush(second))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void publishedServiceVersionsCannotBeUpdated() {
        UUID department = createDepartment("IMMUT");
        UUID serviceId = UUID.randomUUID();
        jdbc.update("insert into services (id, department_id, code, name, description, category, processing_days, status, "
                + "current_version, created_at, updated_at) values (?, ?, ?, 'S', 'D', 'X', 5, 'PUBLISHED', 1, now(), now())",
                serviceId, department, "IMM_" + uniq().toUpperCase());
        jdbc.update("insert into service_versions (service_id, version_number, name, description, category, "
                + "processing_days, fee_amount, form_schema, requirements, published_at) "
                + "values (?, 1, 'S', 'D', 'X', 5, 0, '{}'::jsonb, '[]'::jsonb, now())", serviceId);
        assertThatThrownBy(() -> jdbc.update("update service_versions set name = 'changed' where service_id = ?", serviceId))
                .hasMessageContaining("immutable");
    }
}
