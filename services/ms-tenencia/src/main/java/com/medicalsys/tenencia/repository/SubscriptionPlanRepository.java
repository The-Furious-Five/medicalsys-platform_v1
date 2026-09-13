package com.medicalsys.tenencia.repository;

import com.medicalsys.tenencia.domain.model.SubscriptionPlan;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso al catalogo de planes.
 *
 * <p>Es el unico agregado de este servicio que no esta acotado por organizacion: los planes son
 * globales de la plataforma.
 */
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Short> {

    Optional<SubscriptionPlan> findByCode(String code);

    List<SubscriptionPlan> findByActiveTrueOrderByMonthlyPriceBobAsc();
}
