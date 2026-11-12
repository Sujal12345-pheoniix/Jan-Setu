package com.jansetu.modules.access.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A role granted to a user (table user_roles). Users are referenced by id to keep module boundaries. */
@Entity
@IdClass(UserRoleAssignment.Key.class)
@Table(name = "user_roles")
public class UserRoleAssignment {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "role_id")
    private UUID roleId;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "assigned_by")
    private UUID assignedBy;

    protected UserRoleAssignment() {
    }

    public UserRoleAssignment(UUID userId, UUID roleId, Instant assignedAt, UUID assignedBy) {
        this.userId = userId;
        this.roleId = roleId;
        this.assignedAt = assignedAt;
        this.assignedBy = assignedBy;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public static class Key implements Serializable {
        private UUID userId;
        private UUID roleId;

        public Key() {
        }

        public Key(UUID userId, UUID roleId) {
            this.userId = userId;
            this.roleId = roleId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(userId, k.userId) && Objects.equals(roleId, k.roleId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, roleId);
        }
    }
}
