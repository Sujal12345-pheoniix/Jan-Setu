package com.jansetu.security;

/**
 * Permission codes (RESOURCE:ACTION). The authoritative catalogue and role mapping live in Flyway migration
 * V8; these constants exist so code references are checked by the compiler.
 */
public final class Permissions {

    private Permissions() {
    }

    public static final String USER_READ = "USER:READ";
    public static final String USER_CREATE = "USER:CREATE";
    public static final String USER_UPDATE = "USER:UPDATE";
    public static final String USER_DEACTIVATE = "USER:DEACTIVATE";
    public static final String USER_ASSIGN_ROLE = "USER:ASSIGN_ROLE";

    public static final String ROLE_READ = "ROLE:READ";
    public static final String ROLE_CREATE = "ROLE:CREATE";
    public static final String ROLE_MANAGE = "ROLE:MANAGE";
    public static final String PERMISSION_READ = "PERMISSION:READ";

    public static final String DEPARTMENT_READ = "DEPARTMENT:READ";
    public static final String DEPARTMENT_CREATE = "DEPARTMENT:CREATE";
    public static final String DEPARTMENT_UPDATE = "DEPARTMENT:UPDATE";
    public static final String DEPARTMENT_MANAGE_MEMBERS = "DEPARTMENT:MANAGE_MEMBERS";

    public static final String SERVICE_READ_ALL = "SERVICE:READ_ALL";
    public static final String SERVICE_CREATE = "SERVICE:CREATE";
    public static final String SERVICE_UPDATE = "SERVICE:UPDATE";
    public static final String SERVICE_PUBLISH = "SERVICE:PUBLISH";

    public static final String AUDIT_READ = "AUDIT:READ";

    /** Department-scoped permissions apply to every department (platform-level actors). */
    public static final String SCOPE_ALL_DEPARTMENTS = "SCOPE:ALL_DEPARTMENTS";
}
