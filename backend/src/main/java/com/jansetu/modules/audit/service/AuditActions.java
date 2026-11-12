package com.jansetu.modules.audit.service;

/** Audit action names. Stable strings: audit queries and dashboards depend on them. */
public final class AuditActions {

    private AuditActions() {
    }

    public static final String USER_REGISTERED = "USER_REGISTERED";
    public static final String LOGIN_SUCCEEDED = "LOGIN_SUCCEEDED";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    public static final String TOKEN_REFRESHED = "TOKEN_REFRESHED";
    public static final String REFRESH_TOKEN_REUSE_DETECTED = "REFRESH_TOKEN_REUSE_DETECTED";
    public static final String LOGGED_OUT = "LOGGED_OUT";
    public static final String SESSIONS_REVOKED = "SESSIONS_REVOKED";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String PASSWORD_RESET_REQUESTED = "PASSWORD_RESET_REQUESTED";
    public static final String PASSWORD_RESET_COMPLETED = "PASSWORD_RESET_COMPLETED";

    public static final String USER_CREATED = "USER_CREATED";
    public static final String USER_UPDATED = "USER_UPDATED";
    public static final String USER_STATUS_CHANGED = "USER_STATUS_CHANGED";
    public static final String USER_ROLES_CHANGED = "USER_ROLES_CHANGED";

    public static final String ROLE_CREATED = "ROLE_CREATED";
    public static final String ROLE_PERMISSIONS_CHANGED = "ROLE_PERMISSIONS_CHANGED";

    public static final String DEPARTMENT_CREATED = "DEPARTMENT_CREATED";
    public static final String DEPARTMENT_UPDATED = "DEPARTMENT_UPDATED";
    public static final String DEPARTMENT_STATUS_CHANGED = "DEPARTMENT_STATUS_CHANGED";
    public static final String DEPARTMENT_MEMBER_ADDED = "DEPARTMENT_MEMBER_ADDED";
    public static final String DEPARTMENT_MEMBER_REMOVED = "DEPARTMENT_MEMBER_REMOVED";

    public static final String SERVICE_CREATED = "SERVICE_CREATED";
    public static final String SERVICE_UPDATED = "SERVICE_UPDATED";
    public static final String SERVICE_REQUIREMENTS_CHANGED = "SERVICE_REQUIREMENTS_CHANGED";
    public static final String SERVICE_PUBLISHED = "SERVICE_PUBLISHED";
    public static final String SERVICE_DEPRECATED = "SERVICE_DEPRECATED";

    public static final String AUDIT_LOG_VIEWED = "AUDIT_LOG_VIEWED";
}
