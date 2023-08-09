package com.fedex.aggregator.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Data
@ToString
@AllArgsConstructor(staticName = "create")
public class AggregateResult {

    private Map<String, Optional<Double>> pricing;
    private Map<String, Optional<String>> track;
    private Map<String, Optional<List<String>>> shipments;
}