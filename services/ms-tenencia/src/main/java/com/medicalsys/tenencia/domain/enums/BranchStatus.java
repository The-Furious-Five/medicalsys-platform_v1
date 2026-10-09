package com.medicalsys.tenencia.domain.enums;

/**
 * Estado de una sede fisica ({@code branch.status}).
 *
 * <p>El diccionario no define borrado fisico de sucursales: dar de baja es pasar a
 * {@link #INACTIVE} y conservar la fila, porque citas y facturas ya emitidas la referencian.
 */
public enum BranchStatus {
    ACTIVE,
    INACTIVE;

    public boolean isActive() {
        return this == ACTIVE;
    }
}
