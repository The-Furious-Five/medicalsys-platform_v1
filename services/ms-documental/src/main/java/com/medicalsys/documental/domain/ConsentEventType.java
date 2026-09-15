package com.medicalsys.documental.domain;

public enum ConsentEventType {
    GENERATED,
    SENT_WHATSAPP,
    SENT_EMAIL,
    OPEN,
    FULLY_READ,
    OTP_REQUESTED,
    OTP_VALIDATED,
    SIGNED,
    REJECTED,
    REVOKED,
    DOWNLOADED
}
