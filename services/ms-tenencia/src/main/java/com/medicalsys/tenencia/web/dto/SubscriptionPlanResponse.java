package com.medicalsys.tenencia.web.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Plan comercial publicado en el catalogo de la plataforma.
 *
 * <p>Un limite nulo significa ilimitado, tal como lo declara el diccionario para
 * {@code max_users}, {@code max_branches} y {@code max_storage_gb}.
 */
public record SubscriptionPlanResponse(
        Short id,
        String code,
        String name,
        BigDecimal monthlyPriceBob,
        Integer maxUsers,
        Integer maxBranches,
        Integer maxStorageGb,
        boolean includesWhatsapp,
        boolean includesBilling,
        boolean active,
        OffsetDateTime createdAt) {
}
