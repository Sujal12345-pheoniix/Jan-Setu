package com.jansetu.modules.department.domain;

import com.jansetu.common.error.ApiException;
import com.jansetu.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "departments")
public class Department extends AuditableEntity {

    @Column(nullable = false, length = 32, updatable = false)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "contact_email", length = 254)
    private String contactEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DepartmentStatus status;

    protected Department() {
    }

    public Department(String code, String name, String description, String contactEmail) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.contactEmail = contactEmail;
        this.status = DepartmentStatus.ACTIVE;
    }

    public void rename(String newName) {
        this.name = newName;
    }

    public void describe(String newDescription) {
        this.description = newDescription;
    }

    public void changeContactEmail(String email) {
        this.contactEmail = email;
    }

    public void activate() {
        if (status == DepartmentStatus.ACTIVE) {
            throw ApiException.invalidState("Department is already active.");
        }
        status = DepartmentStatus.ACTIVE;
    }

    public void deactivate() {
        if (status == DepartmentStatus.INACTIVE) {
            throw ApiException.invalidState("Department is already inactive.");
        }
        status = DepartmentStatus.INACTIVE;
    }

    public boolean isActive() {
        return status == DepartmentStatus.ACTIVE;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public DepartmentStatus getStatus() {
        return status;
    }
}
