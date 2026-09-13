package com.medicalsys.historial.client;

import java.util.Locale;
import java.util.regex.Pattern;

/** Formato canonico de los codigos de catalogo consumidos por historial. */
final class CatalogCodes {

    /** CIE-10: letra inicial (sin U), dos digitos y subcategoria opcional. Ej: J00, M54.5. */
    static final Pattern ICD10 = Pattern.compile("^[A-TV-Z][0-9]{2}(\\.[0-9A-Z]{1,4})?$");

    /** Codigo estable de catalogo en mayusculas. Ej: X_RAY, PARACETAMOL_500. */
    static final Pattern CATALOG_CODE = Pattern.compile("^[A-Z][A-Z0-9_]{1,29}$");

    private CatalogCodes() {
    }

    /** Normaliza a mayusculas sin espacios; {@code null} se mantiene para poder validar opcionalidad. */
    static String normalize(String code) {
        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }
}
