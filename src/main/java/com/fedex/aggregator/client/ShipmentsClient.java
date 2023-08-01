package com.fedex.aggregator.client;

import org.springframework.beans.factory.annotation.Autowired;
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
public class ShipmentsClient {

    private final WebClient client;
    @Value("${timeout}")
    private Duration timeout;

    public ShipmentsClient(@Value("${shipments-url}") String shipmentsUrl) {
        this.client = WebClient.builder()
                .baseUrl(shipmentsUrl)
                .build();
    }

    public Mono<Map<String, Optional<List<String>>>> getShipment(List<String> ids) {
        System.out.println(getClass() + " thread = " + Thread.currentThread().getName());
        return client
                .get()
                .uri("?q={ids}", ids.stream().sorted().collect(joining(",")))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Optional<List<String>>>>() {
                }).log()
                .timeout(timeout)
                .onErrorReturn(e -> e instanceof WebClientResponseException || e instanceof TimeoutException,
                        ids.stream().collect(toMap(identity(), v -> Optional.empty(), (a, b) -> a)));
    }
}