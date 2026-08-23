package com.medicalsys.facturacion.repository;

import com.medicalsys.facturacion.domain.model.DocumentSequence;
import com.medicalsys.facturacion.domain.model.DocumentSequenceId;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso al contador de correlativos. */
public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, DocumentSequenceId> {

    /**
     * Bloquea la fila del contador hasta el final de la transaccion ({@code SELECT ... FOR UPDATE}).
     *
     * <p>Es la pieza que impide correlativos repetidos: dos emisiones simultaneas sobre el mismo
     * punto de venta se serializan aqui, de modo que la segunda lee el numero ya incrementado por la
     * primera en vez de leer el mismo valor.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DocumentSequence s where s.id = :id")
    Optional<DocumentSequence> lockById(@Param("id") DocumentSequenceId id);
}
