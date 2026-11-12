package com.jansetu.modules.grievance.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * SUBMITTED → ACKNOWLEDGED → ASSIGNED → IN_PROGRESS → RESOLVED → CLOSED. A resolved grievance may be REOPENED once
 * by the citizen within the reopen window; reassignment keeps the case in ASSIGNED.
 */
public enum GrievanceStatus {
    SUBMITTED, ACKNOWLEDGED, ASSIGNED, IN_PROGRESS, RESOLVED, CLOSED, REOPENED;

    private static final Map<GrievanceStatus, Set<GrievanceStatus>> ALLOWED = Map.of(
            SUBMITTED, EnumSet.of(ACKNOWLEDGED, ASSIGNED),
            ACKNOWLEDGED, EnumSet.of(ASSIGNED),
            ASSIGNED, EnumSet.of(ASSIGNED, IN_PROGRESS),
            IN_PROGRESS, EnumSet.of(ASSIGNED, RESOLVED),
            RESOLVED, EnumSet.of(CLOSED, REOPENED),
            REOPENED, EnumSet.of(ASSIGNED, IN_PROGRESS),
            CLOSED, EnumSet.noneOf(GrievanceStatus.class));

    public boolean canTransitionTo(GrievanceStatus target) {
        return ALLOWED.get(this).contains(target);
    }
}
