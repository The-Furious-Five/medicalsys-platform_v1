package com.medicalsys.marketing.domain.enums;

/** Ciclo de vida del anuncio. */
public enum AdvertisementStatus {
    DRAFT,
    PUBLISHED,
    PAUSED,
    FINISHED;

    /** Solo un anuncio publicado se sirve al portal y acumula impresiones o clics. */
    public boolean isVisible() {
        return this == PUBLISHED;
    }
}
