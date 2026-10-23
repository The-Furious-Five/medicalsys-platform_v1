package com.medicalsys.documental.service;

import java.util.UUID;

public record IntegrityCheckResult(
        UUID documentId,
        String expectedHash,
        String actualHash,
        boolean matches) {
}
