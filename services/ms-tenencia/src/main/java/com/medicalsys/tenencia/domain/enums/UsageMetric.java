package com.medicalsys.tenencia.domain.enums;

/**
 * Cuota medida en {@code plan_usage.metric}.
 *
 * <p>Cada metrica se contrasta contra su limite del plan: {@link #USERS} contra
 * {@code max_users}, {@link #BRANCHES} contra {@code max_branches} y {@link #STORAGE_BYTES}
 * contra {@code max_storage_gb}. La proyeccion es local: quien consume la cuota (identidad,
 * documental) la reporta, y tenencia decide si queda espacio.
 */
public enum UsageMetric {

    /** Usuarios activos de la organizacion; el limite vive en {@code subscription_plan.max_users}. */
    USERS,
    /** Sucursales registradas; el limite vive en {@code subscription_plan.max_branches}. */
    BRANCHES,
    /** Bytes almacenados; el limite del plan se expresa en GB y se convierte al comparar. */
    STORAGE_BYTES;

    /** Bytes que contiene un gigabyte, para contrastar STORAGE_BYTES con {@code max_storage_gb}. */
    public static final long BYTES_PER_GB = 1_073_741_824L;

    /** Traduce el limite del plan a la unidad en la que se mide esta metrica. */
    public long limitFromPlan(int planLimit) {
        return this == STORAGE_BYTES ? planLimit * BYTES_PER_GB : planLimit;
    }
}
