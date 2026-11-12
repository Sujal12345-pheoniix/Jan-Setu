package com.jansetu.modules.department.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Staff membership of a department; determines the user's department data scope. */
@Entity
@Table(name = "department_memberships")
public class DepartmentMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "department_id", nullable = false, updatable = false)
    private UUID departmentId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(length = 100)
    private String position;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "added_by", updatable = false)
    private UUID addedBy;

    protected DepartmentMembership() {
    }

    public DepartmentMembership(UUID departmentId, UUID userId, String position, Instant joinedAt, UUID addedBy) {
        this.departmentId = departmentId;
        this.userId = userId;
        this.position = position;
        this.joinedAt = joinedAt;
        this.addedBy = addedBy;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getPosition() {
        return position;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
