package com.jansetu.modules.catalogue.domain;

public enum ServiceStatus {
    /** Never published; invisible to citizens. */
    DRAFT,
    /** At least one published version; the latest version is visible in the public catalogue. */
    PUBLISHED,
    /** Withdrawn from the catalogue; existing versions remain readable for historical applications. */
    DEPRECATED
}
