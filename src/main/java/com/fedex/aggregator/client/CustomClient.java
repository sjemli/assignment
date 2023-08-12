package com.fedex.aggregator.client;

import lombok.AllArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeoutException;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;


@AllArgsConstructor
@Log
public class CustomClient<T> {

    private final WebClient client;
    private Duration timeout;

    public Mono<Map<String, Optional<T>>> getResult(List<String> ids) {
       log.warning("ids = " + ids);
        return client
                .get()
                .uri("?q={ids}", ids.stream().sorted().collect(joining(",")))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Optional<T>>>() {
                })
                .timeout(timeout)
                .onErrorReturn(e -> e instanceof WebClientResponseException || e instanceof TimeoutException,
                        ids.stream().collect(toMap(identity(),
                                v -> Optional.empty(),
                                (id1, id2) -> id1,
                                TreeMap::new)));
    }
}