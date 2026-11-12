package com.jansetu.modules.access.service;

import com.jansetu.common.api.PageQuery;
import com.jansetu.common.error.ApiException;
import com.jansetu.common.error.ErrorCode;
import com.jansetu.modules.access.domain.Permission;
import com.jansetu.modules.access.domain.Role;
import com.jansetu.modules.access.repository.PermissionRepository;
import com.jansetu.modules.access.repository.RoleRepository;
import com.jansetu.modules.audit.service.AuditActions;
import com.jansetu.modules.audit.service.AuditEvent;
import com.jansetu.modules.audit.service.AuditService;
import com.jansetu.security.AuthenticatedUser;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleService {

    private static final Map<String, String> SORTABLE = Map.of("code", "code", "name", "name", "createdAt", "createdAt");

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final AuditService auditService;

    public RoleService(RoleRepository roleRepository, PermissionRepository permissionRepository,
                       AuditService auditService) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<Role> list(PageQuery pageQuery) {
        return roleRepository.findAll(pageQuery.toPageable(SORTABLE, Sort.by("code")));
    }

    @Transactional(readOnly = true)
    public Role get(UUID id) {
        Role role = roleRepository.findWithPermissionsById(id).orElseThrow(() -> ApiException.notFound("Role"));
        role.getPermissions().size();
        return role;
    }

    @Transactional(readOnly = true)
    public List<Permission> listPermissions() {
        return permissionRepository.findAllByOrderByCategoryAscCodeAsc();
    }

    @Transactional
    public Role create(String code, String name, String description, Set<String> permissionCodes,
                       AuthenticatedUser actor) {
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (roleRepository.existsByCode(normalized)) {
            throw ApiException.duplicate("A role with code " + normalized + " already exists.");
        }
        Set<Permission> permissions = resolvePermissions(permissionCodes, actor);
        Role role = new Role(normalized, name.trim(), description);
        role.replacePermissions(permissions);
        roleRepository.saveAndFlush(role);
        auditService.record(AuditEvent.success(AuditActions.ROLE_CREATED, "ROLE", role.getId())
                .detail("code", normalized)
                .detail("permissions", codes(permissions)));
        return role;
    }

    @Transactional
    public Role replacePermissions(UUID id, Set<String> permissionCodes, Long expectedVersion, AuthenticatedUser actor) {
        Role role = get(id);
        if (!role.isVersion(expectedVersion)) {
            throw ApiException.staleVersion();
        }
        Set<String> before = codes(role.getPermissions());
        Set<Permission> permissions = resolvePermissions(permissionCodes, actor);
        role.replacePermissions(permissions);
        roleRepository.saveAndFlush(role);
        auditService.record(AuditEvent.success(AuditActions.ROLE_PERMISSIONS_CHANGED, "ROLE", role.getId())
                .detail("before", before)
                .detail("after", codes(permissions)));
        return role;
    }

    private Set<Permission> resolvePermissions(Set<String> permissionCodes, AuthenticatedUser actor) {
        List<Permission> found = permissionRepository.findByCodeIn(permissionCodes);
        if (found.size() != permissionCodes.size()) {
            Set<String> unknown = new TreeSet<>(permissionCodes);
            unknown.removeAll(found.stream().map(Permission::getCode).collect(Collectors.toSet()));
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Unknown permission(s): " + String.join(", ", unknown) + ".");
        }
        Set<String> missing = actor.isSystem() ? Set.of() : PrivilegedPermissions.missing(permissionCodes, actor.permissions());
        if (!missing.isEmpty()) {
            throw new ApiException(ErrorCode.PRIVILEGE_ESCALATION,
                    "You cannot put privileged permissions you do not hold into a role: " + String.join(", ", missing) + ".");
        }
        return new HashSet<>(found);
    }

    private static Set<String> codes(Set<Permission> permissions) {
        return permissions.stream().map(Permission::getCode).collect(Collectors.toCollection(TreeSet::new));
    }
}
