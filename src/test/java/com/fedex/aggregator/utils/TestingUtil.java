package com.fedex.aggregator.utils;


import com.fedex.aggregator.model.AggregateResult;
import lombok.experimental.UtilityClass;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.function.Consumer;

import static java.util.Objects.nonNull;

@UtilityClass
public class TestingUtil {

    public static ResponseSpec executeAggregationRequest(
            WebTestClient webTestClient,
            List<String> pricingParams,
            List<String> trackParams,
            List<String> shipmentsParams) {

        var uri = createAggregationUri(pricingParams, trackParams, shipmentsParams);
        return webTestClient.get().uri(uri).exchange();
    }

    private static String createAggregationUri(List<String> pricing,
                                               List<String> track,
                                               List<String> shipments) {

        UriComponentsBuilder builder = UriComponentsBuilder.newInstance().path("/aggregation");

        if (nonNull(pricing) && !pricing.isEmpty()) {
            builder.queryParam("pricing", String.join(",", pricing));
        }
        if (nonNull(track) && !track.isEmpty()) {
            builder.queryParam("track", String.join(",", track));
        }
        if (nonNull(shipments) && !shipments.isEmpty()) {
            builder.queryParam("shipments", String.join(",", shipments));
        }
        return builder.build().encode().toString();
    }

    public static void assertResponse(ResponseSpec responseSpec, Consumer<AggregateResult> aggregateResultConsumer) {
        responseSpec.expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(AggregateResult.class)
                .value(aggregateResultConsumer);
    }
}