package com.medicalsys.facturacion.repository;

import com.medicalsys.facturacion.domain.model.Invoice;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a facturas.
 *
 * <p>Toda consulta esta acotada por {@code organizationPublicId}: una factura de otra organizacion
 * no debe ser alcanzable ni siquiera por identificador.
 */
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    /** Carga la factura con su detalle en una sola consulta; la respuesta siempre lo incluye. */
    @EntityGraph(attributePaths = "items")
    Optional<Invoice> findByOrganizationPublicIdAndPublicId(UUID organizationPublicId, UUID publicId);

    Page<Invoice> findByOrganizationPublicIdOrderByIssuedAtDesc(UUID organizationPublicId, Pageable pageable);

    Page<Invoice> findByOrganizationPublicIdAndPatientPublicIdOrderByIssuedAtDesc(UUID organizationPublicId,
            UUID patientPublicId, Pageable pageable);
}
