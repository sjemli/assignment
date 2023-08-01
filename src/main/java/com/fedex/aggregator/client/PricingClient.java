package com.fedex.aggregator.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeoutException;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

@Service
public class PricingClient {

    private final WebClient client;
    @Value("${timeout}")
    private Duration timeout;

    public PricingClient(@Value("${pricing-url}") String pricingUrl) {
        this.client = WebClient.builder()
                .baseUrl(pricingUrl)
                .build();
    }

    public Mono<Map<String, Optional<Float>>> getPricing(List<String> ids) {
        System.out.println(getClass() + " thread = " + Thread.currentThread().getName());
        return client
                .get()
                .uri("?q={ids}", ids.stream().collect(joining(",")))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Optional<Float>>>() {
                })
                .timeout(timeout)
                .log()
                .onErrorReturn(e -> e instanceof WebClientResponseException || e instanceof TimeoutException,
                        ids.stream().collect(toMap(identity(),
                                v -> Optional.empty(),
                                (id1, id2) -> id1,
                                TreeMap::new)));
    }
}