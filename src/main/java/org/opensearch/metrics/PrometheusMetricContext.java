package org.opensearch.metrics;

import java.util.Arrays;
import java.util.Objects;

public class PrometheusMetricContext {
    private final String name;
    private final String[] labelValues;

    public PrometheusMetricContext(String name, String[] labelValues) {
        this.name = name;
        this.labelValues = labelValues;
    }

    public String name() {
        return name;
    }

    public String[] labelValues() {
        return labelValues;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PrometheusMetricContext that = (PrometheusMetricContext) o;
        return name.equals(that.name) && Arrays.equals(labelValues, that.labelValues);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(name);
        result = 31 * result + Arrays.hashCode(labelValues);
        return result;
    }

    public static PrometheusMetricContext of(String name, String... labelValues) {
        return new PrometheusMetricContext(name, labelValues);
    }
}
