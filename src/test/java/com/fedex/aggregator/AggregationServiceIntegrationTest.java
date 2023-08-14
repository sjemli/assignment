package com.fedex.aggregator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.fedex.aggregator.client.CustomClient.getDefaultResult;
import static com.fedex.aggregator.service.AggregationService.BUFFER_TIMEOUT;
import static com.fedex.aggregator.utils.TestingUtil.assertResponse;
import static com.fedex.aggregator.utils.TestingUtil.executeAggregationRequest;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureWebTestClient(timeout = "10000")
@ActiveProfiles("test")
class AggregationServiceIntegrationTest {

    private static final int WIREMOCK_PORT = 9092;
    private static final String PRICING = "/pricing";
    private static final String TRACK = "/track";
    private static final String SHIPMENTS = "/shipments";
    public static final String COMMA = "%2C";
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Value("${timeout}")
    private Duration timeout;
    @Autowired
    protected WebTestClient webTestClient;

    private WireMockServer wireMockServer;


    @BeforeEach
    public void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT));
        wireMockServer.start();
        wireMockServer.resetAll();
        configureFor("localhost", WIREMOCK_PORT);
    }

    @AfterEach
    public void tearDown() {
        wireMockServer.stop();
    }

    @Test
     void should_return_valid_response_with_duration_over_5_seconds() {
        //Given
        List<String> pricingIds = List.of("CN", "NL");
        List<String> trackIds = List.of("1", "2");
        List<String> shipmentsIds = List.of("1", "2");

        Map<String, Double> pricingResult = Map.of("CN", 2.0, "NL", 1.0);
        Map<String, String> trackResult = Map.of("1", "NEW", "2", "COLLECTING");
        Map<String, List<String>> shipmentsResult = Map.of("1", List.of("box", "box", "pallet"),
                "2", List.of("envelope"));

        stubWithSuccessfulResponse(PRICING, pricingIds, pricingResult);
        stubWithSuccessfulResponse(TRACK, trackIds, trackResult);
        stubWithSuccessfulResponse(SHIPMENTS, shipmentsIds, shipmentsResult);

        //When
        Instant start = Instant.now();

        var response = executeAggregationRequest(webTestClient, pricingIds, trackIds, shipmentsIds);

        //Then
        assertResponse(response , f -> {
            assertThat(Duration.between(start, Instant.now())).isGreaterThan(BUFFER_TIMEOUT);
            assertThat(f.getPricing()).isEqualTo(transformToMapOfOptional(pricingResult));
            assertThat(f.getTrack()).isEqualTo(transformToMapOfOptional(trackResult));
            assertThat(f.getShipments()).isEqualTo(transformToMapOfOptional(shipmentsResult));
        });
    }

    @Test
    void should_return_default_response_if_downstream_services_times_out() {
        //Given
        List<String> pricingIds = List.of("CN", "NL", "TU", "FR", "IT");

        Map<String, Double> pricingResult = Map.of("CN", 2.0, "NL", 1.0, "TU", 2.0,
                "FR", 1.0, "IT", 2.0);

        stubWithSuccessfulResponse(PRICING, pricingIds, pricingResult, 5500);

        //When

        var response = executeAggregationRequest(webTestClient, pricingIds, List.of(), List.of());

        //Then
        assertResponse(response , f -> {
            assertThat(f.getPricing()).isEqualTo(getDefaultResult(pricingIds));
            assertThat(f.getTrack()).isEmpty();
            assertThat(f.getShipments()).isEmpty();
        });
    }


    @Test
     void should_return_default_result_in_case_downstream_services_return_status_503() {
        //Given
        List<String> pricingParams = List.of("DE", "BE");
        List<String> trackParams = List.of("3", "4");
        List<String> shipmentsParams = List.of("3", "4");

        Map<String, Optional<Double>> expectedPricingResult = getDefaultResult(pricingParams);
        Map<String, Optional<String>> expectedTrackResult = getDefaultResult(trackParams);
        Map<String, Optional<List<String>>> expectedShipmentsResult = getDefaultResult(shipmentsParams);

        stubWith503Unavailable(PRICING, pricingParams);
        stubWith503Unavailable(TRACK, trackParams);
        stubWith503Unavailable(SHIPMENTS, shipmentsParams);

        //When
        var response = executeAggregationRequest(webTestClient, pricingParams, trackParams, shipmentsParams);

        //Then
        assertResponse(response , f -> {
            assertThat(f.getPricing()).isEqualTo(expectedPricingResult);
            assertThat(f.getTrack()).isEqualTo(expectedTrackResult);
            assertThat(f.getShipments()).isEqualTo(expectedShipmentsResult);
        });
    }


    @Test
     void testRunningConcurrent() {
        Integer delayMilliseconds = 1000;
        stubFiveAndTwoItemsAndReplyWithDelay(delayMilliseconds);

        List<String> pricingParams1 = List.of("NL", "CN");
        List<String> trackParams1 = List.of("1", "2", "3", "4", "5");
        List<String> shipmentsParams1 = null;

        var response1 = executeAggregationRequest(webTestClient, pricingParams1, trackParams1, shipmentsParams1);

        List<String> pricingParams2 = List.of("US", "CA", "UK");
        List<String> trackParams2 = List.of();
        List<String> shipmentsParams2 = List.of("1", "2", "3", "4", "5");

        var response2 = executeAggregationRequest(webTestClient, pricingParams2, trackParams2, shipmentsParams2);

        List<String> pricingParams3 = List.of("KR", "JP");
        List<String> trackParams3 =List.of("6", "7");
        List<String> shipmentsParams3 = List.of("6", "7");

        var response3 = executeAggregationRequest(webTestClient, pricingParams3, trackParams3, shipmentsParams3);

        assertResponse(response1 , f -> {
            assertThat(f.getPricing()).hasSize(2);
            assertThat(pricingParams1.stream().allMatch(k -> f.getPricing().containsKey(k)));
            assertThat(f.getTrack()).hasSize(5);
            assertThat(trackParams1.stream().allMatch(k -> f.getTrack().containsKey(k)));
            assertThat(f.getShipments()).isEmpty();
        });

        assertResponse(response2 , f -> {
            assertThat(f.getPricing()).hasSize(3);
            assertThat(pricingParams2.stream().allMatch(k -> f.getPricing().containsKey(k)));
            assertThat(f.getTrack()).isEmpty();
            assertThat(f.getShipments()).hasSize(5);
            assertThat(shipmentsParams2.stream().allMatch(k -> f.getShipments().containsKey(k)));
        });


        assertResponse(response3 , f -> {
            assertThat(f.getPricing()).hasSize(2);
            assertThat(pricingParams3.stream().allMatch(k -> f.getPricing().containsKey(k)));
            assertThat(f.getTrack()).hasSize(2);
            assertThat(trackParams3.stream().allMatch(k -> f.getTrack().containsKey(k)));
            assertThat(f.getShipments()).hasSize(2);
            assertThat(shipmentsParams3.stream().allMatch(k -> f.getShipments().containsKey(k)));
        });
    }

    @Test
    void testMoreThanBatchSize() {
        //Given
        Integer delayMilliseconds = 1000;
        stubFiveAndTwoItemsAndReplyWithDelay(delayMilliseconds);

        List<String> pricingParams = List.of("NL", "CN", "US", "CA", "UK", "KR", "JP");
        List<String> trackParams = List.of("1", "2", "3", "4", "5", "6", "7");
        List<String> shipmentsParams = List.of("1", "2", "3", "4", "5", "6", "7");

        //When
        var response = executeAggregationRequest(webTestClient, pricingParams, trackParams, shipmentsParams);


        //Then
        assertResponse(response , f -> {
            assertThat(f.getPricing()).hasSize(7);
            assertThat(pricingParams.stream().allMatch(k -> f.getPricing().containsKey(k)));
            assertThat(f.getTrack()).hasSize(7);
            assertThat(trackParams.stream().allMatch(k -> f.getTrack().containsKey(k)));
            assertThat(f.getShipments()).hasSize(7);
            assertThat(shipmentsParams.stream().allMatch(k -> f.getShipments().containsKey(k)));
        });
    }

    private <T> void stubWithSuccessfulResponse(String paramName, List<String> params, Map<String, T> result) {
        stubWithSuccessfulResponse(paramName, params, result, 0);
    }
    private <T> void stubWithSuccessfulResponse(String paramName, List<String> params, Map<String, T> result,
                                                Integer delayInMillis) {
        Optional.ofNullable(params)
                .filter(list -> !list.isEmpty()).map(l -> paramName + "?q=" + String.join(COMMA, params))
                .ifPresent(url -> createStub(url, getJson(result), delayInMillis));
    }

    @SneakyThrows
    private <T> String getJson(Map<String, T> result) {
        return objectMapper.writeValueAsString(result);
    }


    private <T>  Map<String, Optional<T>> transformToMapOfOptional(Map<String, T> pricingResult) {
        return pricingResult.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> Optional.ofNullable(e.getValue())));
    }

    private void stubFiveAndTwoItemsAndReplyWithDelay(Integer delayMilliseconds) {
        createStub("/pricing?q=CA%2CCN%2CNL%2CUK%2CUS", """
        {"CA": 1.0, "CN": 2.0, "NL": 1.0, "UK": 2.0, "US": 1.0}""", delayMilliseconds);
        createStub("/track?q=1%2C2%2C3%2C4%2C5", """
                {"1": "NEW", "2": "COLLECTING", "3": "NEW", "4": "COLLECTING", "5": "DELIVERING"}""", delayMilliseconds);
        createStub("/shipments?q=1%2C2%2C3%2C4%2C5", """
                {"1": ["box", "box", "pallet"], "2": ["envelope"],
                 "3": ["envelope"], "4": ["box"], "5": ["envelope"]}""", delayMilliseconds);
        createStub("/pricing?q=JP%2CKR", """
                {"KR": 1, "JP": 2}""", delayMilliseconds);
        createStub("/track?q=6%2C7", """
        {"6": "NEW", "7": "COLLECTING"}""", delayMilliseconds);
        createStub("/shipments?q=6%2C7", """
                {"6": ["box"], "7": ["envelope"]}""", delayMilliseconds);
    }

    private void createStub(String path, String response, Integer delay) {
        stubFor(get(urlEqualTo(path))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withFixedDelay(delay)
                        .withBody(response)));
    }

    private void stubWith503Unavailable(String paramName, List<String> params) {
        Optional.ofNullable(params)
                .filter(list -> !list.isEmpty()).map(l -> paramName + "?q=" + String.join("%2C", params))
                .ifPresent(url ->
                        stubFor(get(urlEqualTo(url)).willReturn(aResponse().withStatus(503))));
    }
}