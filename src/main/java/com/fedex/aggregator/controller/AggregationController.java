package com.fedex.aggregator.controller;

import com.fedex.aggregator.service.AggregationService;
import com.fedex.aggregator.model.AggregateResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@RestController
public class AggregationController {

    @Autowired
    private AggregationService aggregationService;


    @GetMapping
    Mono<ResponseEntity<AggregateResult>> aggregate(@RequestParam Optional<List<String>> pricing,
                                                    @RequestParam Optional<List<String>> track,
                                                    @RequestParam Optional<List<String>> shipments) {
        return aggregationService.aggregate(pricing, track, shipments)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}