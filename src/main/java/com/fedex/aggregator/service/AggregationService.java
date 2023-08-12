package com.fedex.aggregator.service;

import com.fedex.aggregator.client.CustomClient;
import com.fedex.aggregator.model.AggregateResult;
import jakarta.annotation.PostConstruct;
import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static java.time.temporal.ChronoUnit.SECONDS;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toMap;
import static reactor.util.concurrent.Queues.SMALL_BUFFER_SIZE;

@Service
@Log
public class AggregationService {

    //For Sink thread-safety
    private static final Sinks.EmitFailureHandler HANDLER = (signalType, emission) -> true;
    private static final int BUFFER_MAX_SIZE = 5;
    private static final Duration BUFFER_TIMEOUT = Duration.of(5, SECONDS);

    @Autowired
    private CustomClient<Double> pricingClient;
    @Autowired
    private CustomClient<String> trackClient;
    @Autowired
    private CustomClient<List<String>> shipmentsClient;

    private final Many<List<String>> pricingSink;
    private final Many<List<String>> trackSink;
    private final Many<List<String>> shipmentsSink;

    private Flux<Map<String, Optional<Double>>> pricingFlux;
    private Flux<Map<String, Optional<String>>> trackFlux;
    private Flux<Map<String, Optional<List<String>>>> shipmentsFlux;

    public AggregationService() {
        pricingSink = Sinks.many().multicast().onBackpressureBuffer(SMALL_BUFFER_SIZE, false);
        trackSink = Sinks.many().multicast().onBackpressureBuffer(SMALL_BUFFER_SIZE, false);
        shipmentsSink = Sinks.many().multicast().onBackpressureBuffer(SMALL_BUFFER_SIZE, false);
    }

    @PostConstruct
    public void setUp() {
        pricingFlux = getFlux(pricingSink, pricingClient::getResult);
        trackFlux = getFlux(trackSink, trackClient::getResult);
        shipmentsFlux = getFlux(shipmentsSink, shipmentsClient::getResult);
        pricingFlux.subscribe();
        trackFlux.subscribe();
        shipmentsFlux.subscribe();
    }

    private <T> Flux<Map<String, Optional<T>>> getFlux(
            Many<List<String>> sink,
            Function<List<String>, Mono<Map<String, Optional<T>>>> getResult) {
        return sink.asFlux().log().flatMapIterable(Function.identity())
                .bufferTimeout(BUFFER_MAX_SIZE, BUFFER_TIMEOUT)
                .flatMap(getResult)
                .share();
    }

    public Mono<AggregateResult> aggregate(Optional<List<String>> pricing,
                                           Optional<List<String>> track,
                                           Optional<List<String>> shipments) {

        log.warning("pricing = " + pricing);
        Mono<Map<String, Optional<Double>>> pricingMono =
                Mono.create(monoSink -> create(monoSink, pricingFlux, pricing));

        Mono<Map<String, Optional<List<String>>>> shipmentsMono =
                Mono.create(monoSink -> create(monoSink, shipmentsFlux, shipments));

        Mono<Map<String, Optional<String>>> trackMono =
                Mono.create(monoSink -> create(monoSink, trackFlux, track));


        pricing.filter(not(List::isEmpty)).ifPresent(l -> pricingSink.emitNext(l, HANDLER));
        track.filter(not(List::isEmpty)).ifPresent(l -> trackSink.emitNext(l, HANDLER));
        shipments.filter(not(List::isEmpty)).ifPresent(l -> shipmentsSink.emitNext(l, HANDLER));

        return Mono.zip(pricingMono, trackMono, shipmentsMono)
                .map(t -> AggregateResult.create(t.getT1(), t.getT2(), t.getT3()));
    }

    private <T> void create(MonoSink<Map<String, Optional<T>>> monoSink,
                            Flux<Map<String, Optional<T>>> flux,
                            Optional<List<String>> maybeQueryParams) {
        maybeQueryParams.ifPresentOrElse(
                params -> create(monoSink, flux, params),
                () -> monoSink.success(Map.of())
        );
    }
    private <T> void create(MonoSink<Map<String, Optional<T>>> monoSink,
                            Flux<Map<String, Optional<T>>> flux,
                            List<String> queryParams) {
        Map<String, Optional<T>> result = new HashMap<>();
        flux
                .doOnNext(g -> result.putAll(subset(g, queryParams)))
                .doOnCancel(() -> monoSink.success(result))
                .takeUntil(f -> result.keySet().containsAll(queryParams))
                .subscribe();
    }


    private <T> Map<String, Optional<T>> subset(Map<String, Optional<T>> map, List<String> subsetKeys) {
        return map.entrySet().stream()
                .filter(x -> subsetKeys.contains(x.getKey()))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}