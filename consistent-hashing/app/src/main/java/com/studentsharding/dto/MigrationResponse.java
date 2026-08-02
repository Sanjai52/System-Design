package com.studentsharding.dto;

public record MigrationResponse(String action, String nodeId, long recordsMigrated, Distribution distribution) {
}
