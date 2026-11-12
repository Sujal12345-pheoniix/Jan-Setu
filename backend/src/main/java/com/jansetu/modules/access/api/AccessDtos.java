package com.jansetu.modules.access.api;

import com.jansetu.modules.access.domain.Permission;
import com.jansetu.modules.access.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/** Request/response DTOs of the access module. */
public final class AccessDtos {

    private AccessDtos() {
    }

    public record PermissionResponse(String code, String category, String description) {
        static PermissionResponse from(Permission p) {
            return new PermissionResponse(p.getCode(), p.getCategory(), p.getDescription());
        }
    }

    public record RoleSummaryResponse(UUID id, String code, String name, String description, boolean systemRole,
                                      long version) {
        static RoleSummaryResponse from(Role r) {
            return new RoleSummaryResponse(r.getId(), r.getCode(), r.getName(), r.getDescription(), r.isSystemRole(),
                    r.getVersion());
        }
    }

    public record RoleResponse(UUID id, String code, String name, String description, boolean systemRole,
                               Set<String> permissions, long version) {
        static RoleResponse from(Role r) {
            return new RoleResponse(r.getId(), r.getCode(), r.getName(), r.getDescription(), r.isSystemRole(),
                    r.getPermissions().stream().map(Permission::getCode).collect(Collectors.toCollection(TreeSet::new)),
                    r.getVersion());
        }
    }

    public record CreateRoleRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{1,63}$",
                    message = "must be 2-64 letters, digits or underscores, starting with a letter") String code,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 500) String description,
            @NotNull @Size(max = 100) Set<@NotBlank String> permissions) {
    }

    public record ReplacePermissionsRequest(
            @NotNull @Size(max = 100) Set<@NotBlank String> permissions,
            @NotNull Long version) {
    }
}
