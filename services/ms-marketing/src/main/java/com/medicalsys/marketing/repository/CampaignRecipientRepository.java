package com.medicalsys.marketing.repository;

import com.medicalsys.marketing.domain.enums.RecipientStatus;
import com.medicalsys.marketing.domain.model.CampaignRecipient;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a la audiencia materializada de una campana. */
public interface CampaignRecipientRepository extends JpaRepository<CampaignRecipient, Long> {

    Page<CampaignRecipient> findByCampaignId(Long campaignId, Pageable pageable);

    Page<CampaignRecipient> findByCampaignIdAndStatus(Long campaignId, RecipientStatus status, Pageable pageable);

    Optional<CampaignRecipient> findByCampaignIdAndPatientPublicId(Long campaignId, UUID patientPublicId);

    Optional<CampaignRecipient> findByNotificationPublicId(UUID notificationPublicId);

    long countByCampaignIdAndStatus(Long campaignId, RecipientStatus status);

    long countByCampaignIdAndStatusNot(Long campaignId, RecipientStatus status);

    boolean existsByCampaignPublicIdAndPatientPublicId(UUID campaignPublicId, UUID patientPublicId);

    /**
     * Vacia la audiencia antes de rematerializarla.
     *
     * <p>Se borra en bloque en vez de fila por fila: una audiencia de 5.000 pacientes recalculada con
     * {@code deleteAll} generaria 5.000 DELETE y otros tantos SELECT previos.
     *
     * <p>{@code flushAutomatically} escribe lo pendiente antes del DELETE. No se usa
     * {@code clearAutomatically}: vaciaria el contexto de persistencia y con el desprenderia la
     * campana que el caso de uso tiene en la mano, de modo que su nuevo total de destinatarios no
     * llegaria a guardarse. Quien llama no mantiene destinatarios cargados, asi que no hay entidades
     * obsoletas que limpiar.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from CampaignRecipient r where r.campaign.id = :campaignId")
    int deleteByCampaignId(@Param("campaignId") Long campaignId);
}
