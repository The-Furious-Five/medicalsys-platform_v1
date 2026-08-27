package com.medicalsys.pacientes.web.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Pagina de resultados con metadatos estables.
 *
 * <p>No se expone la estructura interna de Spring Data: el contrato del MVP declara estos campos y
 * no cambia si cambia la libreria.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
