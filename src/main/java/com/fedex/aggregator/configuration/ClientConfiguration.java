package com.fedex.aggregator.configuration;

import com.fedex.aggregator.client.CustomClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

@Configuration
public class ClientConfiguration {
    @Value("${timeout}")
    private Duration timeout;

    @Bean
    public CustomClient<Double> pricingClient(@Value("${pricing-url}") String pricingUrl) {
        return createClient(pricingUrl);
    }

    @Bean
    public CustomClient<String> trackClient(@Value("${track-url}") String trackUrl) {
        return createClient(trackUrl);
    }

    @Bean
    public CustomClient<List<String>> shipmentsClient(@Value("${shipments-url}") String shipmentsUrl) {
        return createClient(shipmentsUrl);
    }

    private <T> CustomClient<T> createClient(String url) {
        var webClient = WebClient.builder()
                .baseUrl(url)
                .build();
        return new CustomClient<>(webClient, timeout);
    }
}