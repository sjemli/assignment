package com.fedex.aggregator.model;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties
@Getter
public class AggregationProperties {
    private String pricingUrl;
    private String trackUrl;
    private String shipmentsUrl;
    private Duration timeout;
}