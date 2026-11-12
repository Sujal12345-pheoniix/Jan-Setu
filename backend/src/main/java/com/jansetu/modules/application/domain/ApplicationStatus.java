package com.jansetu.modules.application.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Application lifecycle (docs/WORKFLOW.md):
 * DRAFT → SUBMITTED → UNDER_REVIEW → (CLARIFICATION_REQUIRED → RESUBMITTED → UNDER_REVIEW)* → PENDING_APPROVAL
 * → APPROVED → COMPLETED, with REJECTED and WITHDRAWN as terminal alternatives and PENDING_APPROVAL → UNDER_REVIEW
 * when a reviewer returns a case for correction.
 */
public enum ApplicationStatus {
    DRAFT, SUBMITTED, UNDER_REVIEW, CLARIFICATION_REQUIRED, RESUBMITTED, PENDING_APPROVAL, APPROVED, REJECTED,
    COMPLETED, WITHDRAWN;

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED = Map.of(
            DRAFT, EnumSet.of(SUBMITTED, WITHDRAWN),
            SUBMITTED, EnumSet.of(UNDER_REVIEW, WITHDRAWN),
            UNDER_REVIEW, EnumSet.of(CLARIFICATION_REQUIRED, PENDING_APPROVAL, REJECTED),
            CLARIFICATION_REQUIRED, EnumSet.of(RESUBMITTED),
            RESUBMITTED, EnumSet.of(UNDER_REVIEW),
            PENDING_APPROVAL, EnumSet.of(APPROVED, REJECTED, UNDER_REVIEW),
            APPROVED, EnumSet.of(COMPLETED),
            REJECTED, EnumSet.noneOf(ApplicationStatus.class),
            COMPLETED, EnumSet.noneOf(ApplicationStatus.class),
            WITHDRAWN, EnumSet.noneOf(ApplicationStatus.class));

    public boolean canTransitionTo(ApplicationStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    public boolean isTerminal() {
        return ALLOWED.get(this).isEmpty();
    }
}
