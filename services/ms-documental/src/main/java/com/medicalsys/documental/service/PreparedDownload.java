package com.medicalsys.documental.service;

import java.nio.file.Path;

public record PreparedDownload(
        Path temporaryFile,
        String originalFilename,
        String mimeType,
        long sizeBytes,
        String sha256Hash) {
}
