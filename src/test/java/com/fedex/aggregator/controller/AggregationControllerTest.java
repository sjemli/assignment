package com.fedex.aggregator.controller;

import com.fedex.aggregator.model.AggregateResult;
import com.fedex.aggregator.service.AggregationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.fedex.aggregator.utils.TestingUtil.assertResponse;
import static com.fedex.aggregator.utils.TestingUtil.executeAggregationRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebFluxTest
class AggregationControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private AggregationService aggregationService;


    @Test
    void shouldReturnValidAggregateResponseWhenAggregationControllerIsCalled() {
        //Given
        Map<String, Optional<Double>> pricingResult = Map.of("NL", Optional.of(14.242090605778),
                "CN", Optional.of(20.503467806384));

        Map<String, Optional<String>> trackResult = Map.of("109347263", Optional.empty(),
                "123456891", Optional.of("COLLECTING"));

        Map<String, Optional<List<String>>> shipmentsResult =
                Map.of("109347263", Optional.of(List.of("box", "box", "pallet")),
                        "123456891", Optional.empty());

        List<String> pricingIds = new ArrayList<>(pricingResult.keySet());
        List<String> trackIds = new ArrayList<>(trackResult.keySet());
        List<String> shipmentsIds = new ArrayList<>(shipmentsResult.keySet());

        when(aggregationService.aggregate(Optional.of(pricingIds), Optional.of(trackIds), Optional.of(shipmentsIds)))
                .thenReturn(Mono.just(AggregateResult.create(pricingResult, trackResult, shipmentsResult)));
        //When
        var response = executeAggregationRequest(webTestClient, pricingIds, trackIds, shipmentsIds);

        //Then
        assertResponse(response, result -> {
            assertThat(result.getPricing()).isEqualTo(pricingResult);
            assertThat(result.getTrack()).isEqualTo(trackResult);
            assertThat(result.getShipments()).isEqualTo(shipmentsResult);
        });

        verify(aggregationService).aggregate(Optional.of(pricingIds),
                Optional.of(trackIds),
                Optional.of(shipmentsIds));
    }
}