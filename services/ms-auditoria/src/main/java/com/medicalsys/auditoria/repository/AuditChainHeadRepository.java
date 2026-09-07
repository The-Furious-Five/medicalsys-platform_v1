package com.medicalsys.auditoria.repository;

import com.medicalsys.auditoria.domain.model.AuditChainHead;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

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
}
