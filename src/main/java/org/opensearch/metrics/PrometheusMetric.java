package org.opensearch.metrics;

public class PrometheusMetric {
    private final String name;
    private final String[] labelValues;
    private final double value;

    public PrometheusMetric(String name, String[] labelValues, double value) {
        this.name = name;
        this.labelValues = labelValues;
        this.value = value;
    }

    public String name() {
        return name;
    }

    public String[] labelValues() {
        return labelValues;
    }

    public double value() {
        return value;
    }
}