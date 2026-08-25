package com.medicalsys.documental.service;

import java.text.Normalizer;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class FilenameSanitizer {

    public String safe(String originalFilename) {
        String filename = originalFilename == null ? "" : originalFilename.trim();
        filename = filename.replace('\\', '/');
        int slash = filename.lastIndexOf('/');
        if (slash >= 0) {
            filename = filename.substring(slash + 1);
        }
        filename = Normalizer.normalize(filename, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "_")
                .replaceAll("_+", "_")
                .replaceFirst("^\\.+", "");
        if (filename.isBlank()) {
            filename = "document";
        }
        return filename.length() <= 120 ? filename : filename.substring(filename.length() - 120);
    }
}
