package com.medicalsys.notificaciones.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.medicalsys.notificaciones.common.RequestOrganization;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Pide la audiencia de una campana a ms-marketing (contrapartida del claim check de
 * {@code marketing.campana}: el evento no trae destinatarios, se piden por
 * {@code GET /internal/v1/campanas/{id}/destinatarios}).
 */
@Component
public class MarketingCampaignClient {

    private static final int PAGE_SIZE = 200;

    private final WebClient webClient;

    public MarketingCampaignClient(WebClient.Builder builder,
            @Value("${medicalsys.notifications.marketing.base-url}") String baseUrl) {
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    /** Recorre todas las paginas de destinatarios SELECTED de la campana. */
    public Flux<Recipient> fetchRecipients(UUID organizationId, UUID campaignId) {
        return fetchPage(organizationId, campaignId, 0)
                .expand(page -> page.page() + 1 < page.totalPages()
                        ? fetchPage(organizationId, campaignId, page.page() + 1)
                        : Mono.empty())
                .concatMap(page -> Flux.fromIterable(page.content()));
    }

    private Mono<RecipientPage> fetchPage(UUID organizationId, UUID campaignId, int page) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/internal/v1/campanas/{campaignId}/destinatarios")
                        .queryParam("page", page)
                        .queryParam("size", PAGE_SIZE)
                        .build(campaignId))
                .header(RequestOrganization.HEADER, organizationId.toString())
                .retrieve()
                .bodyToMono(RecipientPage.class);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Recipient(UUID patientId, String fullName, String status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RecipientPage(List<Recipient> content, int page, int size, long totalElements, int totalPages) {
    }
}
