package com.medicalsys.marketing.repository;

import com.medicalsys.marketing.domain.model.Promotion;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a los cupones del tenant. */
public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    Optional<Promotion> findByPublicIdAndOrganizationPublicId(UUID publicId, UUID organizationPublicId);

    Optional<Promotion> findByOrganizationPublicIdAndCouponCode(UUID organizationPublicId, String couponCode);

    Page<Promotion> findByOrganizationPublicId(UUID organizationPublicId, Pageable pageable);

    Page<Promotion> findByOrganizationPublicIdAndActive(UUID organizationPublicId, boolean active,
            Pageable pageable);

    boolean existsByOrganizationPublicIdAndCouponCode(UUID organizationPublicId, String couponCode);

    /**
     * Toma el cupon con bloqueo de escritura para canjearlo.
     *
     * <p>Es el mismo patron con el que ms-facturacion protege su correlativo. Sin el, dos cajas que
     * cobran a la vez leerian el mismo {@code current_redemptions} y el limite {@code max_redemptions}
     * dejaria de ser un limite. El bloqueo dura lo que la transaccion del canje, que es corta.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Promotion p where p.organizationPublicId = :organizationId "
            + "and p.couponCode = :couponCode")
    Optional<Promotion> lockByCouponCode(@Param("organizationId") UUID organizationId,
            @Param("couponCode") String couponCode);
}
