package com.medicalsys.marketing.mapper;

import com.medicalsys.marketing.common.DateTimes;
import com.medicalsys.marketing.domain.model.Advertisement;
import com.medicalsys.marketing.domain.model.AudienceSegment;
import com.medicalsys.marketing.domain.model.Campaign;
import com.medicalsys.marketing.domain.model.CampaignRecipient;
import com.medicalsys.marketing.domain.model.LoyaltyPointsMovement;
import com.medicalsys.marketing.domain.model.LoyaltyProgram;
import com.medicalsys.marketing.domain.model.OutboxEvent;
import com.medicalsys.marketing.domain.model.PatientActivity;
import com.medicalsys.marketing.domain.model.Promotion;
import com.medicalsys.marketing.domain.model.PromotionRedemption;
import com.medicalsys.marketing.domain.model.RefPatient;
import com.medicalsys.marketing.web.dto.AdvertisementResponse;
import com.medicalsys.marketing.web.dto.CampaignRecipientResponse;
import com.medicalsys.marketing.web.dto.CampaignResponse;
import com.medicalsys.marketing.web.dto.CampaignSummaryResponse;
import com.medicalsys.marketing.web.dto.CouponRedemptionResponse;
import com.medicalsys.marketing.web.dto.LoyaltyMovementResponse;
import com.medicalsys.marketing.web.dto.LoyaltyProgramResponse;
import com.medicalsys.marketing.web.dto.OutboxEventResponse;
import com.medicalsys.marketing.web.dto.PatientActivityResponse;
import com.medicalsys.marketing.web.dto.PromotionResponse;
import com.medicalsys.marketing.web.dto.SegmentResponse;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Traduce entidades a la representacion publica del contrato.
 *
 * <p>Ninguna entidad JPA sale del servicio: la API expone {@code publicId} y nunca el id tecnico
 * local, que es una decision de acoplamiento -- un consumidor que aprendiera a usar el {@code BIGINT}
 * quedaria atado a la clave interna de esta base.
 */
@Component
public class MarketingMapper {

    /** Segmento con su criterio ya vinculado a tipos. */
    public SegmentResponse toResponse(AudienceSegment segment) {
        return new SegmentResponse(
                segment.getPublicId(),
                segment.getName(),
                segment.getDescription(),
                segment.getCriteria(),
                segment.usesClinicalData(),
                segment.getEstimatedSize(),
                DateTimes.toOffset(segment.getLastEvaluatedAt()),
                segment.isActive(),
                DateTimes.toOffset(segment.getCreatedAt()));
    }

    /** Campana completa, con el nombre del segmento y el codigo del cupon ya resueltos. */
    public CampaignResponse toResponse(Campaign campaign) {
        return new CampaignResponse(
                campaign.getPublicId(),
                campaign.getName(),
                campaign.getType(),
                campaign.getObjective(),
                campaign.getChannel(),
                campaign.getMessageTemplatePublicId(),
                campaign.getAudienceSegment() == null ? null : campaign.getAudienceSegment().getPublicId(),
                campaign.getAudienceSegment() == null ? null : campaign.getAudienceSegment().getName(),
                campaign.getPromotion() == null ? null : campaign.getPromotion().getPublicId(),
                campaign.getPromotion() == null ? null : campaign.getPromotion().getCouponCode(),
                DateTimes.toOffset(campaign.getStartsAt()),
                DateTimes.toOffset(campaign.getEndsAt()),
                campaign.getBudgetBob(),
                campaign.getStatus(),
                campaign.getTotalRecipients(),
                campaign.getTotalSent(),
                campaign.getTotalRead(),
                campaign.getTotalConverted(),
                DateTimes.toOffset(campaign.getCreatedAt()),
                DateTimes.toOffset(campaign.getUpdatedAt()));
    }

    /** Proyeccion breve para el listado de campanas. */
    public CampaignSummaryResponse toSummary(Campaign campaign) {
        return new CampaignSummaryResponse(
                campaign.getPublicId(),
                campaign.getName(),
                campaign.getType(),
                campaign.getStatus(),
                DateTimes.toOffset(campaign.getStartsAt()),
                campaign.getTotalRecipients(),
                campaign.getTotalConverted());
    }

    /** Destinatario materializado; el nombre llega de la replica y puede faltar. */
    public CampaignRecipientResponse toResponse(CampaignRecipient recipient, String fullName) {
        return new CampaignRecipientResponse(
                recipient.getPatientPublicId(),
                fullName,
                recipient.getStatus(),
                recipient.getExclusionReason(),
                recipient.getNotificationPublicId(),
                recipient.getGeneratedAppointmentPublicId(),
                DateTimes.toOffset(recipient.getEvaluatedAt()));
    }

    /** Cupon. */
    public PromotionResponse toResponse(Promotion promotion) {
        return new PromotionResponse(
                promotion.getPublicId(),
                promotion.getCouponCode(),
                promotion.getName(),
                promotion.getDescription(),
                promotion.getDiscountType(),
                promotion.getValue(),
                promotion.getServiceOfferingPublicId(),
                promotion.getMaxRedemptions(),
                promotion.getRedemptionsPerPatient(),
                promotion.getCurrentRedemptions(),
                promotion.getValidFrom(),
                promotion.getValidUntil(),
                promotion.isActive(),
                DateTimes.toOffset(promotion.getCreatedAt()));
    }

    /** Canje registrado. */
    public CouponRedemptionResponse toResponse(PromotionRedemption redemption, BigDecimal netAmountBob,
            boolean alreadyRedeemed) {
        return new CouponRedemptionResponse(
                redemption.getPublicId(),
                redemption.getPromotion().getPublicId(),
                redemption.getPromotion().getCouponCode(),
                redemption.getPatientPublicId(),
                redemption.getInvoicePublicId(),
                redemption.getDiscountedAmountBob(),
                netAmountBob,
                DateTimes.toOffset(redemption.getRedeemedAt()),
                alreadyRedeemed);
    }

    /** Anuncio. */
    public AdvertisementResponse toResponse(Advertisement advertisement) {
        return new AdvertisementResponse(
                advertisement.getPublicId(),
                advertisement.getOrganizationPublicId() == null,
                advertisement.getTitle(),
                advertisement.getContent(),
                advertisement.getImageDocumentPublicId(),
                advertisement.getTargetUrl(),
                advertisement.getServiceOfferingPublicId(),
                advertisement.getPlacement(),
                advertisement.getPriority(),
                advertisement.getTargetAudience(),
                DateTimes.toOffset(advertisement.getValidFrom()),
                DateTimes.toOffset(advertisement.getValidUntil()),
                advertisement.getStatus(),
                advertisement.getImpressions(),
                advertisement.getClicks(),
                DateTimes.toOffset(advertisement.getCreatedAt()));
    }

    /** Programa de fidelizacion. */
    public LoyaltyProgramResponse toResponse(LoyaltyProgram program) {
        return new LoyaltyProgramResponse(
                program.getPublicId(),
                program.getName(),
                program.getPointsPerBob(),
                program.getBobPerPoint(),
                program.getPointsValidityMonths(),
                program.isActive(),
                DateTimes.toOffset(program.getCreatedAt()));
    }

    /** Asiento del libro mayor, con el valor en bolivianos ya calculado. */
    public LoyaltyMovementResponse toResponse(LoyaltyPointsMovement movement) {
        return new LoyaltyMovementResponse(
                movement.getPublicId(),
                movement.getPatientPublicId(),
                movement.getType(),
                movement.getPoints(),
                movement.getResultingBalance(),
                movement.getProgram().valueOf(Math.abs(movement.getPoints())),
                movement.getInvoicePublicId(),
                movement.getAppointmentPublicId(),
                movement.getReason(),
                DateTimes.toOffset(movement.getOccurredAt()));
    }

    /** Proyeccion comercial de un paciente. */
    public PatientActivityResponse toResponse(PatientActivity activity, RefPatient patient,
            String lastServiceOfferingName) {
        return new PatientActivityResponse(
                activity.getPatientPublicId(),
                patient == null ? null : patient.getFullName(),
                activity.getFirstVisitOn(),
                activity.getLastVisitOn(),
                activity.getTotalAppointments(),
                activity.getTotalNoShows(),
                activity.getTotalBilledBob(),
                activity.getLastServiceOfferingPublicId(),
                lastServiceOfferingName,
                activity.getSuggestedNextVisitOn(),
                patient != null && patient.isMarketingOptOut(),
                DateTimes.toOffset(activity.getUpdatedAt()));
    }

    /** Evento saliente. */
    public OutboxEventResponse toResponse(OutboxEvent event) {
        return new OutboxEventResponse(
                event.getEventPublicId(),
                event.getEventType(),
                event.getAggregatePublicId(),
                event.getPartitionKey(),
                event.getPayload(),
                event.getStatus(),
                event.getAttempts(),
                DateTimes.toOffset(event.getCreatedAt()),
                DateTimes.toOffset(event.getPublishedAt()));
    }
}
