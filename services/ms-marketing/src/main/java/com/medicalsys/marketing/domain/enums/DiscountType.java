package com.medicalsys.marketing.domain.enums;

/** Forma en que la promocion reduce el importe. */
public enum DiscountType {
    /** {@code value} es un porcentaje entre 0 y 100 aplicado sobre la base. */
    PERCENTAGE,
    /** {@code value} es un monto fijo en bolivianos, acotado a la base. */
    FIXED_AMOUNT,
    /** La prestacion indicada queda en cero; exige {@code serviceOfferingId}. */
    FREE_SERVICE
}
