package com.medicalsys.auditoria.repository;

import com.medicalsys.auditoria.domain.model.AuditChainHead;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditChainHeadRepository extends JpaRepository<AuditChainHead, UUID> {

    /**
     * Toma la punta de la cadena con bloqueo de escritura ({@code SELECT ... FOR UPDATE}).
     *
     * <p>Sin el bloqueo, dos asientos simultaneos de la misma organizacion leerian el mismo
     * {@code lastSequenceNumber} y uno de los dos fallaria contra la restriccion UNIQUE, o peor,
     * quedarian encadenados al mismo predecesor. El bloqueo es por organizacion, asi que dos
     * clinicas distintas siguen asentando en paralelo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuditChainHead> findByOrganizationId(UUID organizationId);

    /**
     * Lee la punta de la cadena <b>sin</b> bloquearla.
     *
     * <p>Existe aparte porque el bloqueo de arriba pertenece al camino de escritura. La
     * verificacion de integridad corre en una transaccion de solo lectura, y MySQL rechaza un
     * {@code SELECT ... FOR UPDATE} ahi dentro con «Cannot execute statement in a READ ONLY
     * transaction»: el endpoint respondia 500. Verificar la cadena tampoco necesita el bloqueo,
     * porque no la modifica.
     */
    @Query("select h from AuditChainHead h where h.organizationId = :organizationId")
    Optional<AuditChainHead> peekByOrganizationId(@Param("organizationId") UUID organizationId);
}
