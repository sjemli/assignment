package com.fedex.aggregator.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Data
@AllArgsConstructor
public class Result {
    private Map<String, Optional<String>> pricing;
    private Map<String, Optional<String>> track;
    private Map<String, Optional<List<String>>> shipments;
}