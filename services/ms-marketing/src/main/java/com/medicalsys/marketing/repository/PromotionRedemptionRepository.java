package com.medicalsys.marketing.repository;

import com.medicalsys.marketing.domain.model.PromotionRedemption;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso al registro de canjes de cupones. */
public interface PromotionRedemptionRepository extends JpaRepository<PromotionRedemption, Long> {

    long countByPromotionIdAndPatientPublicId(Long promotionId, UUID patientPublicId);

    /** Idempotencia del canje: si la factura ya consumio el cupon, se devuelve el canje existente. */
    Optional<PromotionRedemption> findByPromotionIdAndInvoicePublicId(Long promotionId, UUID invoicePublicId);
}
