package com.fedex.aggregator.client;

import com.fedex.aggregator.model.AggregationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.TimeoutException;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

@Service
public class ShipmentsClient {

    private final WebClient client;
    @Autowired
    private AggregationProperties aggregationProperties;

    public ShipmentsClient() {
        this.client = WebClient.builder()
                .baseUrl(aggregationProperties.getShipmentsUrl())
                .build();
    }

    public Mono<Map<String, Optional<List<String>>>> getShipment(Optional<List<String>> ids) {
        return ids.map(l -> this.client
                        .get()
                        .uri("{q}", l.stream().sorted().collect(joining(",")))
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<Map<String, Optional<List<String>>>>() {
                        })
                        .timeout(aggregationProperties.getTimeout())
                        .onErrorReturn(e -> e instanceof WebClientResponseException || e instanceof TimeoutException,
                                l.stream().collect(toMap(identity(), v -> Optional.empty(), (id1, id2)-> id1, TreeMap::new))))
                .orElse(Mono.just(Collections.emptyMap()));
    }
}