package com.medicalsys.marketing.repository;

import com.medicalsys.marketing.common.persistence.Uuids;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Lecturas contra las dos vistas de {@code marketing_db}.
 *
 * <p>Las vistas no son decorativas: {@code v_loyalty_balance} y {@code v_campaign_performance} son
 * las consultas que sirven de verdad el saldo de puntos y el rendimiento de campana. Estan en la
 * base porque agregan sobre el libro mayor y sobre los contadores, y asi la formula de las tasas
 * vive en un unico lugar en vez de repetirse en cada consumidor.
 */
@Repository
public class MarketingViewRepository {

    private final JdbcClient jdbcClient;

    public MarketingViewRepository(DataSource dataSource) {
        this.jdbcClient = JdbcClient.create(dataSource);
    }

    /**
     * Saldo de puntos de un paciente en un programa.
     *
     * @param balancePoints  saldo derivado de la suma de asientos
     * @param lastMovementAt instante del ultimo asiento
     */
    public record LoyaltyBalanceRow(int balancePoints, Instant lastMovementAt) {
    }

    /**
     * Rendimiento de una campana con sus tasas ya calculadas.
     *
     * @param sendRatePct       enviados sobre destinatarios
     * @param readRatePct       leidos sobre enviados
     * @param conversionRatePct conversiones sobre enviados
     * @param excludedOptOut    destinatarios excluidos por haber pedido no recibir publicidad
     */
    public record CampaignPerformanceRow(BigDecimal sendRatePct, BigDecimal readRatePct,
            BigDecimal conversionRatePct, long excludedOptOut) {
    }

    /** Lee el saldo desde la vista; vacio si el paciente no tiene ningun asiento en el programa. */
    public Optional<LoyaltyBalanceRow> loyaltyBalance(Long programId, UUID patientPublicId) {
        return jdbcClient.sql("""
                        SELECT balance_points, last_movement_at
                          FROM v_loyalty_balance
                         WHERE loyalty_program_id = :programId
                           AND patient_public_id = :patientId
                        """)
                .param("programId", programId)
                .param("patientId", Uuids.toBytes(patientPublicId))
                .query((rs, rowNum) -> new LoyaltyBalanceRow(
                        rs.getInt("balance_points"),
                        rs.getTimestamp("last_movement_at") == null
                                ? null
                                : rs.getTimestamp("last_movement_at").toInstant()))
                .optional();
    }

    /** Lee el rendimiento de una campana desde la vista. */
    public Optional<CampaignPerformanceRow> campaignPerformance(UUID campaignPublicId) {
        return jdbcClient.sql("""
                        SELECT send_rate_pct, read_rate_pct, conversion_rate_pct, excluded_opt_out
                          FROM v_campaign_performance
                         WHERE public_id = :publicId
                        """)
                .param("publicId", Uuids.toBytes(campaignPublicId))
                .query((rs, rowNum) -> new CampaignPerformanceRow(
                        rs.getBigDecimal("send_rate_pct"),
                        rs.getBigDecimal("read_rate_pct"),
                        rs.getBigDecimal("conversion_rate_pct"),
                        rs.getLong("excluded_opt_out")))
                .optional();
    }
}
