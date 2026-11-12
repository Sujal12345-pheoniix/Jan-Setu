package com.jansetu.modules.access.domain;

import com.jansetu.common.error.ApiException;
import com.jansetu.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "roles")
public class Role extends AuditableEntity {

    @Column(nullable = false, length = 64, updatable = false)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "system_role", nullable = false, updatable = false)
    private boolean systemRole;

    @ManyToMany
    @JoinTable(name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> permissions = new HashSet<>();

    protected Role() {
    }

    public Role(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.systemRole = false;
    }

    /** System roles are defined by migrations; their permissions cannot be changed through the API. */
    public void replacePermissions(Set<Permission> newPermissions) {
        if (systemRole) {
            throw ApiException.invalidState("System role permissions are managed by database migrations.");
        }
        permissions.clear();
        permissions.addAll(newPermissions);
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

    public boolean isSystemRole() {
        return systemRole;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }
}
