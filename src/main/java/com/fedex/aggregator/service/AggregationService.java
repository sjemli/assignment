package com.fedex.aggregator.service;

import com.fedex.aggregator.client.PricingClient;
import com.fedex.aggregator.client.ShipmentsClient;
import com.fedex.aggregator.client.TrackClient;
import com.fedex.aggregator.model.AggregateResult;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static java.time.temporal.ChronoUnit.SECONDS;
import static java.util.function.Predicate.not;

@Service
public class AggregationService {

    @Autowired
    private PricingClient pricingClient;
    @Autowired
    private TrackClient trackClient;
    @Autowired
    private ShipmentsClient shipmentsClient;

    private final Many<List<String>> pricingSink;
    private final Many<List<String>> trackSink;
    private final Many<List<String>> shipmentsSink;
    Flux<Mono<Map<String, Optional<Float>>>> pricingMonoFlux;
    Flux<Mono<Map<String, Optional<String>>>> trackMonoFlux;
    Flux<Mono<Map<String, Optional<List<String>>>>> shipmentsMonoFlux;

    public AggregationService() {
        pricingSink = Sinks.many().multicast().onBackpressureBuffer();
        trackSink = Sinks.many().multicast().onBackpressureBuffer();
        shipmentsSink = Sinks.many().multicast().onBackpressureBuffer();

    }

    @PostConstruct
    public void setUp() {
        pricingMonoFlux = pricingSink.asFlux().log().flatMapIterable(Function.identity())
                .bufferTimeout(5, Duration.of(5, SECONDS))
                .map(pricingClient::getPricing);

        trackMonoFlux = trackSink.asFlux().log().flatMapIterable(Function.identity())
                .bufferTimeout(5, Duration.of(5, SECONDS))
                .map(trackClient::getTrack);

        shipmentsMonoFlux = shipmentsSink.asFlux().log().flatMapIterable(Function.identity())
                .bufferTimeout(5, Duration.of(5, SECONDS))
                .map(shipmentsClient::getShipment);
    }

    public Mono<AggregateResult> aggregate(Optional<List<String>> pricing,
                                           Optional<List<String>> track,
                                           Optional<List<String>> shipments) {


        pricing.filter(not(List::isEmpty)).ifPresent(l -> pricingSink.tryEmitNext(l));
        track.filter(not(List::isEmpty)).ifPresent(l -> trackSink.tryEmitNext(l));
        shipments.filter(not(List::isEmpty)).ifPresent(l -> shipmentsSink.tryEmitNext(l));


        return Flux.zip(pricingMonoFlux, trackMonoFlux, shipmentsMonoFlux)
                .flatMap(tuple -> Mono.zip(tuple.getT1(), tuple.getT2(), tuple.getT3()))
                .map(t -> new AggregateResult(t.getT1(), t.getT2(), t.getT3()))
                .next();
    }

}