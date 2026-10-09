package com.medicalsys.marketing.repository;

import com.medicalsys.marketing.domain.enums.AdvertisementPlacement;
import com.medicalsys.marketing.domain.enums.AdvertisementStatus;
import com.medicalsys.marketing.domain.model.Advertisement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a los anuncios: los del tenant y los institucionales de la plataforma. */
public interface AdvertisementRepository extends JpaRepository<Advertisement, Long> {

    /**
     * Anuncio visible para el tenant: el suyo o uno institucional.
     *
     * <p>La condicion {@code organizationPublicId is null} no es una fuga entre tenants: un anuncio
     * sin organizacion es, por definicion del esquema, de la plataforma entera.
     */
    @Query("select a from Advertisement a where a.publicId = :publicId "
            + "and (a.organizationPublicId = :organizationId or a.organizationPublicId is null)")
    Optional<Advertisement> findVisible(@Param("publicId") UUID publicId,
            @Param("organizationId") UUID organizationId);

    /** Anuncio propio del tenant; excluye los institucionales, que este tenant no puede editar. */
    Optional<Advertisement> findByPublicIdAndOrganizationPublicId(UUID publicId, UUID organizationPublicId);

    @Query("select a from Advertisement a where (a.organizationPublicId = :organizationId "
            + "or a.organizationPublicId is null)")
    Page<Advertisement> findAllVisible(@Param("organizationId") UUID organizationId, Pageable pageable);

    /** Usado por la siembra de demostracion para no duplicar un anuncio ya creado. */
    boolean existsByOrganizationPublicIdAndTitle(UUID organizationPublicId, String title);

    /** Anuncios que el portal debe mostrar ahora, por prioridad y luego por vigencia. */
    @Query("select a from Advertisement a where (a.organizationPublicId = :organizationId "
            + "or a.organizationPublicId is null) and a.placement = :placement and a.status = :status "
            + "and a.validFrom <= :now and a.validUntil > :now "
            + "order by a.priority asc, a.validUntil asc")
    List<Advertisement> findLive(@Param("organizationId") UUID organizationId,
            @Param("placement") AdvertisementPlacement placement,
            @Param("status") AdvertisementStatus status,
            @Param("now") Instant now,
            Pageable pageable);
}
