package com.fedex.aggregator.client;

import lombok.AllArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;


@AllArgsConstructor
public class CustomClient<T> {

    private final WebClient webClient;
    private Duration timeout;

    public Mono<Map<String, Optional<T>>> getResult(List<String> ids) {
        return webClient
                .get()
                .uri("?q={ids}", String.join(",", ids))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Optional<T>>>() {
                })
                .timeout(timeout)
                .onErrorReturn(e -> e instanceof WebClientResponseException || e instanceof TimeoutException,
                        getDefaultResult(ids));
    }

    public static <T> Map<String, Optional<T>> getDefaultResult(List<String> ids) {
        return ids.stream().distinct().collect(toMap(identity(), v -> Optional.empty()));
    }
}