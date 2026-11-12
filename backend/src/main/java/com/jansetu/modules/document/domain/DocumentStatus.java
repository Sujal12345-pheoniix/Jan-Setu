package com.jansetu.modules.document.domain;

/**
 * UPLOADED/QUARANTINED/SCANNING exist in the schema for a future malware-scanner integration. Without a scanner,
 * validated uploads become AVAILABLE immediately (scan status SKIPPED).
 */
public enum DocumentStatus {
    UPLOADED,
    QUARANTINED,
    SCANNING,
    AVAILABLE,
    VERIFIED,
    REJECTED
}
