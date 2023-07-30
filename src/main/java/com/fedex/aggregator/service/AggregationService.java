package com.fedex.aggregator.service;

import com.fedex.aggregator.client.PricingClient;
import com.fedex.aggregator.client.ShipmentsClient;
import com.fedex.aggregator.client.TrackClient;
import com.fedex.aggregator.model.AggregateResult;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
@Service
public class AggregationService {

    private PricingClient pricingClient;
    private TrackClient trackClient;
    private ShipmentsClient shipmentsClient;

    public Mono<AggregateResult> aggregate(Optional<List<String>> pricing,
                                           Optional<List<String>> track,
                                           Optional<List<String>> shipments) {
        return Mono.zip(
                pricingClient.getPricing(pricing),
                trackClient.getTrack(track),
                shipmentsClient.getShipment(shipments)
        )
                .map(t -> AggregateResult.create(t.getT1(), t.getT2(), t.getT3()));
    }
}