package com.fedex.aggregator.service;

import com.fedex.aggregator.model.Result;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

public class AggregationService {
    public Mono<Result> aggregate(Optional<List<String>> pricing, Optional<List<String>> track, Optional<List<String>> shipments) {
        return null;
    }
}
