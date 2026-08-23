package com.medicalsys.facturacion.web.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Pagina de resultados con metadatos estables.
 *
 * <p>No se expone la estructura interna de Spring Data: el contrato del MVP declara estos campos y
 * no cambia si cambia la libreria.
 *
 * @param content       elementos de la pagina
 * @param page          indice de pagina, base cero
 * @param size          tamano solicitado
 * @param totalElements total de elementos que cumplen el filtro
 * @param totalPages    total de paginas
 * @param <T>           tipo del elemento
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    /** Adapta una pagina de Spring Data al contrato publico. */
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
