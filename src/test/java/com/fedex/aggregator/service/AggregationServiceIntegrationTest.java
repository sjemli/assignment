package com.fedex.aggregator.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.fedex.aggregator.model.AggregateResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest
class AggregationServiceIntegrationTest {

    private final int port = 8080;
    private WireMockServer wireMockServer;
    private final Duration timeout = Duration.ofSeconds(5);

    @Autowired
    private AggregationService aggregationService;

    @BeforeEach
    public void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(port));
        wireMockServer.start();
        wireMockServer.resetAll();
        configureFor("localhost", port);
    }

    @AfterEach
    public void tearDown() {
        wireMockServer.stop();
    }

    @Test
    public void testExceed5Secs() {
        stubTwoItems();

        Mono<AggregateResult> mono = aggregationService
                .aggregate(Optional.of(List.of("NL", "CN")),
                        Optional.of(List.of("1", "2")),
                        Optional.of(List.of("1", "2")));


        Duration duration = StepVerifier
                .create(mono)
                .thenConsumeWhile(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result.getShipments()).hasSize(2);
                    assertThat(result.getPricing()).hasSize(2);
                    assertThat(result.getTrack()).hasSize(2);
                    return true;
                })
                .verifyComplete();
        assertThat(duration).isGreaterThan(Duration.ofSeconds(5));
    }

    @Test
    public void testIf503Unavailable() {
        stubWith503Unavailable();

        Mono<AggregateResult> mono = aggregationService
                .aggregate(Optional.of(List.of("DE", "BE")),
                        Optional.of(List.of("3", "4")),
                        Optional.of(List.of("3", "4")));

        StepVerifier
                .create(mono)
                .thenConsumeWhile(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result.getShipments()).hasSize(2);
                    assertThat(result.getPricing()).hasSize(2);
                    assertThat(result.getTrack()).hasSize(2);
                    return true;
                })
                .thenCancel()
                .verifyLater()
                .verify();
    }


    @Test
    public void testRunningConcurrent() throws InterruptedException {
        Integer delayMilliseconds = 1000;
        stubFiveAndTwoItemsAndReplyWithDelay(delayMilliseconds);

        Mono<AggregateResult> mono1 = aggregationService
                .aggregate(Optional.of(List.of("NL", "CN")),
                        Optional.of(List.of("1", "2", "3", "4", "5")),
                        Optional.empty());
        Mono<AggregateResult> mono2 = aggregationService
                .aggregate(Optional.of(List.of("US", "CA", "UK")), Optional.empty(),
                        Optional.of(List.of("1", "2", "3", "4", "5")));

        //We are waiting to make sure the first five does not get mixed with next items,
        // this is because we mock the external service and expecting these fives come together
        Thread.sleep(110);

        Mono<AggregateResult> mono3 = aggregationService
                .aggregate(Optional.of(List.of("KR", "JP")), Optional.of(List.of("6", "7")), Optional.of(List.of("6", "7")));

        StepVerifier verifier1 = StepVerifier
                .create(mono1)
                .thenConsumeWhile(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result.getPricing()).hasSize(2);
                    assertThat(result.getTrack()).hasSize(5);
                    assertThat(result.getShipments()).hasSize(0);
                    return true;
                }).thenCancel()
                .verifyLater();

        StepVerifier verifier2 = StepVerifier
                .create(mono2)
                .expectSubscription()
                .thenConsumeWhile(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result.getPricing()).hasSize(3);
                    assertThat(result.getTrack()).hasSize(0);
                    assertThat(result.getShipments()).hasSize(5);
                    return true;
                }).thenCancel()
                .verifyLater();

        Duration duration1 = verifier1.verify();
        Duration duration2 = verifier2.verify();

        //To show we call external services in one batch instead of two different single calls
        assertThat(duration1).isLessThan(Duration.ofSeconds(2L * delayMilliseconds));
        assertThat(duration2).isLessThan(Duration.ofSeconds(2L * delayMilliseconds));


        StepVerifier verifier3 = StepVerifier
                .create(mono3)
                .expectSubscription()
                .thenConsumeWhile(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result.getPricing()).hasSize(2);
                    assertThat(result.getTrack()).hasSize(2);
                    assertThat(result.getShipments()).hasSize(2);
                    return true;
                }).thenCancel()
                .verifyLater();
        Duration duration3 = verifier3.verify();

        //To show it wait 5 seconds before making a call
       // assertThat(duration3).isGreaterThan(Duration.ofMillis(Duration.ofSeconds(5).toMillis() + delayMilliseconds));
    }

    @Test
    public void testMoreThanBatchSize() {
        Integer delayMilliseconds = 1000;
        wireMockServer.resetToDefaultMappings();
        stubFiveAndTwoItemsAndReplyWithDelay(delayMilliseconds);

        Mono<AggregateResult> mono1 = aggregationService
                .aggregate(Optional.of(List.of("NL", "CN", "US", "CA", "UK", "KR", "JP")),
                        Optional.of(List.of("1", "2", "3", "4", "5", "6", "7")),
                        Optional.of(List.of("1", "2", "3", "4", "5", "6", "7")));

        StepVerifier verifier1 = StepVerifier
                .create(mono1)
                .thenConsumeWhile(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result.getPricing()).hasSize(7);
                    assertThat(result.getTrack()).hasSize(7);
                    assertThat(result.getShipments()).hasSize(7);
                    return true;
                }).thenCancel()
                .verifyLater();
        Duration duration1 = verifier1.verify();

        //To show we call external services in one batch instead of two different single calls
        assertThat(duration1).isGreaterThan(Duration.ofMillis(delayMilliseconds + timeout.toMillis()));
        assertThat(duration1).isLessThan(Duration.ofMillis(delayMilliseconds + timeout.toMillis() + 1000L));
    }

    private void stubTwoItems() {
        createStub("/pricing?q=CN%2CNL", "{\"CN\": 2, \"NL\": 1}");
        createStub("/track?q=1%2C2", "{\"1\": \"NEW\", \"2\": \"COLLECTING\"}");
        createStub("/shipments?q=1%2C2", "{\"1\": [\"box\", \"box\", \"pallet\"], \"2\": [\"envelope\"]}");
    }

    private void stubFiveAndTwoItemsAndReplyWithDelay(Integer delayMilliseconds) {
        createStub("/pricing?q=CA%2CCN%2CNL%2CUK%2CUS", "{\"CA\": 1, \"CN\": 2, \"NL\": 1, \"UK\": 2, \"US\": 1}", delayMilliseconds);
        createStub("/track?q=1%2C2%2C3%2C4%2C5", "{\"1\": \"NEW\", \"2\": \"COLLECTING\", " +
                "\"3\": \"NEW\", \"4\": \"COLLECTING\", " +
                "\"5\": \"DELIVERING\"}", delayMilliseconds);
        createStub("/shipments?q=1%2C2%2C3%2C4%2C5", "{\"1\": [\"box\", \"box\", \"pallet\"], " +
                "\"2\": [\"envelope\"], \"3\": [\"envelope\"], \"4\": [\"box\"], " +
                "\"5\": [\"envelope\"]}", delayMilliseconds);

        createStub("/pricing?q=JP%2CKR", "{\"KR\": 1, \"JP\": 2}", delayMilliseconds);
        createStub("/track?q=6%2C7", "{\"6\": \"NEW\", \"7\": \"COLLECTING\"}", delayMilliseconds);
        createStub("/shipments?q=6%2C7", "{\"6\": [\"box\"], " + "\"7\": [\"envelope\"]}", delayMilliseconds);
    }

    private void createStub(String path, String response) {
        createStub(path, response, 0);
    }

    private void createStub(String path, String response, Integer delay) {
        stubFor(get(urlEqualTo(path))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withFixedDelay(delay)
                        .withBody(response)));
    }

    private void stubWith503Unavailable() {
        stubFor(get(urlEqualTo("/pricing?q=BE%2CDE")).willReturn(aResponse().withStatus(503)));
        stubFor(get(urlEqualTo("/track?q=3%2C4")).willReturn(aResponse().withStatus(503)));
        stubFor(get(urlEqualTo("/shipments?q=3%2C4")).willReturn(aResponse().withStatus(503)));
    }
}