package com.fedex.aggregator.controller;

import com.fedex.aggregator.model.AggregateResult;
import com.fedex.aggregator.service.AggregationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebFluxTest(AggregationController.class)
class AggregationControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private AggregationService aggregationService;


    @Test
    void test() {
        List<String> pricingIds = List.of("NL", "CN");
        List<String> trackIds = List.of("1", "2");
        List<String> shipmentsIds = List.of("1", "2");

        when(aggregationService.aggregate(Optional.of(pricingIds), Optional.of(trackIds), Optional.of(shipmentsIds)))
                .thenReturn(Mono.just(AggregateResult.create(Map.of(), Map.of(), Map.of())));

        var uri = createAggregationUri(pricingIds, List.of("1", "2"), List.of("1", "2"));
        executeAggregationRequest(uri, f -> {
            assertThat(f.getPricing()).hasSize(2);
            assertThat(pricingIds.stream().allMatch(k -> f.getPricing().containsKey(k)));
            assertThat(f.getTrack()).hasSize(2);
            assertThat(pricingIds.stream().allMatch(k -> f.getTrack().containsKey(k)));
            assertThat(f.getShipments()).hasSize(2);
            assertThat(pricingIds.stream().allMatch(k -> f.getShipments().containsKey(k)));
        });

        verify(aggregationService).aggregate(Optional.of(pricingIds),
                                             Optional.of(trackIds),
                                             Optional.of(shipmentsIds));
    }

    void executeAggregationRequest(String uri, Consumer<AggregateResult> aggregateResultConsumer) {
        webTestClient.get().uri(uri)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(AggregateResult.class)
                .value(aggregateResultConsumer);
    }

    String createAggregationUri(List<String> pricing,
                                List<String> track,
                                List<String> shipments) {

        UriComponentsBuilder builder = UriComponentsBuilder.newInstance().path("/aggregation");

        if (!track.isEmpty()) {
            builder.queryParam("track", String.join(",", track));
        }
        if (!pricing.isEmpty()) {
            builder.queryParam("pricing", String.join(",", pricing));
        }
        if (!shipments.isEmpty()) {
            builder.queryParam("shipments", String.join(",", shipments));
        }

        return builder.build().encode().toString();
    }

}