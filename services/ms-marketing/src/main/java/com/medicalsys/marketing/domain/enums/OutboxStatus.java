package com.medicalsys.marketing.domain.enums;

/** Estado de publicacion de un evento del outbox. */
public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    ERROR
}
