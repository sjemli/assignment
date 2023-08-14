package com.fedex.aggregator.service;

import com.fedex.aggregator.client.CustomClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AggregationServiceTest {

    @Mock
    private CustomClient<Double> pricingClient;
    @Mock
    private CustomClient<String> trackClient;
    @Mock
    private CustomClient<List<String>> shipmentsClient;

    @Test
    void should_return_aggregateResult_in_over_5secs_Given_valid_params_of_number_less_than_5() {

        //Given
        Map<String, Optional<Double>> pricingResult = Map.of("NL", Optional.of(14.242090605778),
                "CN", Optional.of(20.503467806384));

        Map<String, Optional<String>> trackResult = Map.of("109347263", Optional.empty(),
                "123456891", Optional.of("COLLECTING"));

        Map<String, Optional<List<String>>> shipmentsResult =
                Map.of("109347263", Optional.of(List.of("box", "box", "pallet")),
                        "123456891", Optional.empty());

        var pricingIds = new ArrayList<>(pricingResult.keySet());
        var trackIds = new ArrayList<>(trackResult.keySet());
        var shipmentsIds = new ArrayList<>(shipmentsResult.keySet());

        when(pricingClient.getResult(pricingIds))
                .thenReturn(Mono.just(pricingResult));
        when(trackClient.getResult(trackIds))
                .thenReturn(Mono.just(trackResult));
        when(shipmentsClient.getResult(shipmentsIds))
                .thenReturn(Mono.just(shipmentsResult));
        var aggregationService = new AggregationService(pricingClient, trackClient, shipmentsClient);

        //When
        var aggregateResultMono = aggregationService.aggregate(Optional.of(pricingIds), Optional.of(trackIds),
                Optional.of(shipmentsIds));

        //Then
        Duration duration = StepVerifier
                .create(aggregateResultMono)
                .thenConsumeWhile(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result.getPricing()).isEqualTo(pricingResult);
                    assertThat(result.getTrack()).isEqualTo(trackResult);
                    assertThat(result.getShipments()).isEqualTo(shipmentsResult);
                    return true;
                })
                .thenCancel()
                .verifyLater()
                .verify();

        assertThat(duration).isGreaterThan(Duration.ofSeconds(5));
    }

    @Test
    void should_return_aggregateResult_in_less_than_5secs_Given_valid_params_of_number_equalTo_5() {

        //Given
        Map<String, Optional<Double>> pricingResult = Map.of("NL", Optional.of(14.242090605778),
                "CN", Optional.of(56.503467806384), "ES", Optional.of(20.503467806384),
                "IT", Optional.of(11.503467806384), "FR", Optional.empty());

        Map<String, Optional<String>> trackResult = Map.of("1093472680", Optional.empty(),
                "123456891", Optional.of("COLLECTING"),
                "109347263", Optional.empty(),
                "123456845", Optional.of("DELIVERING"),
                "109347262", Optional.of("NEW"));

        Map<String, Optional<List<String>>> shipmentsResult =
                Map.of("109347263", Optional.of(List.of("box", "box", "pallet")),
                        "123456891", Optional.empty(),
                        "109347262", Optional.of(List.of("box", "pallet")),
                        "123456892", Optional.empty(),
                        "109347261", Optional.of(List.of("box")));

        var pricingIds = new ArrayList<>(pricingResult.keySet());
        var trackIds = new ArrayList<>(trackResult.keySet());
        var shipmentsIds = new ArrayList<>(shipmentsResult.keySet());

        var aggregationService = new AggregationService(pricingClient, trackClient, shipmentsClient);
        when(pricingClient.getResult(pricingIds))
                .thenReturn(Mono.just(pricingResult));
        when(trackClient.getResult(trackIds))
                .thenReturn(Mono.just(trackResult));
        when(shipmentsClient.getResult(shipmentsIds))
                .thenReturn(Mono.just(shipmentsResult));

        //When
        var aggregateResultMono = aggregationService.aggregate(Optional.of(pricingIds), Optional.of(trackIds),
                Optional.of(shipmentsIds));

        //Then
        Duration duration = StepVerifier
                .create(aggregateResultMono)
                .thenConsumeWhile(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result.getPricing()).isEqualTo(pricingResult);
                    assertThat(result.getTrack()).isEqualTo(trackResult);
                    assertThat(result.getShipments()).isEqualTo(shipmentsResult);
                    return true;
                })
                .thenCancel()
                .verifyLater()
                .verify();

        assertThat(duration).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    void should_return_aggregateResult_with_empty_maps_given_no_params_or_emptyList() {
        //Given
        Optional<List<String>> pricingIds = Optional.of(List.of());
        Optional<List<String>> trackIds = Optional.empty();
        Optional<List<String>> shipmentsIds =Optional.empty();

        var aggregationService = new AggregationService(pricingClient, trackClient, shipmentsClient);

        //When
        var aggregateResultMono = aggregationService.aggregate(pricingIds, trackIds, shipmentsIds);

        //Then
        Duration duration = StepVerifier
                .create(aggregateResultMono)
                .thenConsumeWhile(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result.getPricing()).isEmpty();
                    assertThat(result.getTrack()).isEmpty();
                    assertThat(result.getShipments()).isEmpty();
                    return true;
                })
                .thenCancel()
                .verifyLater()
                .verify();

        assertThat(duration).isLessThan(Duration.ofSeconds(5));
    }
}