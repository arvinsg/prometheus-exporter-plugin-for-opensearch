package org.opensearch.metrics;

import java.util.Arrays;
import java.util.Objects;

public class PrometheusMetricRegistration {
    private final String name;
    private final String[] labels;
    private final String help;

    public PrometheusMetricRegistration(String name, String[] labels, String help) {
        this.name = name;
        this.labels = labels;
        this.help = help;
    }

    public String name() {
        return name;
    }

    public String[] labels() {
        return labels;
    }

    public String help() {
        return help;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PrometheusMetricRegistration that = (PrometheusMetricRegistration) o;
        return name.equals(that.name) && Arrays.equals(labels, that.labels) && help.equals(that.help);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(name, help);
        result = 31 * result + Arrays.hashCode(labels);
        return result;
    }
}