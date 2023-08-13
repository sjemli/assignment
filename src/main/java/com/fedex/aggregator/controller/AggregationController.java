package com.fedex.aggregator.controller;

import com.fedex.aggregator.service.AggregationService;
import com.fedex.aggregator.model.AggregateResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/aggregation")
public class AggregationController {

    @Autowired
    private AggregationService aggregationService;


    @GetMapping
    Mono<ResponseEntity<AggregateResult>> aggregate(@RequestParam Optional<List<String>> pricingParams,
                                                    @RequestParam Optional<List<String>> trackParams,
                                                    @RequestParam Optional<List<String>> shipmentsParams) {
        return aggregationService.aggregate(pricingParams, trackParams, shipmentsParams)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}