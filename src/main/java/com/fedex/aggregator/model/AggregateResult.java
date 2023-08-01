package com.fedex.aggregator.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Data
@ToString
//@AllArgsConstructor(staticName = "create")
public class AggregateResult {

    public AggregateResult(Map<String, Optional<Float>> pricing, Map<String, Optional<String>> track, Map<String, Optional<List<String>>> shipments) {
        this.pricing = pricing;
        this.track = track;
        this.shipments = shipments;
        System.out.println("Aggregate");
    }

    private Map<String, Optional<Float>> pricing;
    private Map<String, Optional<String>> track;
    private Map<String, Optional<List<String>>> shipments;
}