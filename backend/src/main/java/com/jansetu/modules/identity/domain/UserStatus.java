package com.jansetu.modules.identity.domain;

public enum UserStatus {
    ACTIVE,
    /** Temporarily blocked (e.g. investigation); can be reactivated. */
    SUSPENDED,
    /** Account closed; can be reactivated by an administrator. Records are retained. */
    DEACTIVATED
}
