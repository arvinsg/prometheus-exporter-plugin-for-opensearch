package org.opensearch.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.common.metrics.CounterMetric;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.ConcurrentCollections;

public class IndexMetricCollector {
    public static final Logger logger = LogManager.getLogger(IndexMetricCollector.class);

    public static final String SEARCH_LATENCY_METRIC_PREFIX = "coordinator_search_latency_millis";
    public static final String BULK_LATENCY_METRIC_PREFIX = "coordinator_bulk_latency_millis";
    public static final String P50_METRIC_SUFFIX = "_p50";
    public static final String P90_METRIC_SUFFIX = "_p90";
    public static final String P95_METRIC_SUFFIX = "_p95";
    public static final String P99_METRIC_SUFFIX = "_p99";
    public static final String AVERAGE_METRIC_SUFFIX = "_average";
    public static final String COUNT_METRIC_SUFFIX = "_count";

    public static final String INDEX_SHARD_CPU_TIME_METRIC = "index_shard_cpu_time_nanosecond";
    public static final String INDEX_SHARD_CPU_PERCENT_METRIC = "index_shard_cpu_percent";

    public static final String INDEX_SHARD_MEMORY_ALLOCATION_METRIC = "index_shard_memory_allocation_bytes";

    public static final List<PrometheusMetricRegistration> metricRegistrations;

    static {
        List<PrometheusMetricRegistration> mrs = new ArrayList<>();
        mrs.add(makeRegistration(SEARCH_LATENCY_METRIC_PREFIX + COUNT_METRIC_SUFFIX));
        mrs.add(makeRegistration(SEARCH_LATENCY_METRIC_PREFIX + AVERAGE_METRIC_SUFFIX));
        mrs.add(makeRegistration(SEARCH_LATENCY_METRIC_PREFIX + P50_METRIC_SUFFIX));
        mrs.add(makeRegistration(SEARCH_LATENCY_METRIC_PREFIX + P90_METRIC_SUFFIX));
        mrs.add(makeRegistration(SEARCH_LATENCY_METRIC_PREFIX + P95_METRIC_SUFFIX));
        mrs.add(makeRegistration(SEARCH_LATENCY_METRIC_PREFIX + P99_METRIC_SUFFIX));

        mrs.add(makeRegistration(BULK_LATENCY_METRIC_PREFIX + COUNT_METRIC_SUFFIX));
        mrs.add(makeRegistration(BULK_LATENCY_METRIC_PREFIX + AVERAGE_METRIC_SUFFIX));
        mrs.add(makeRegistration(BULK_LATENCY_METRIC_PREFIX + P50_METRIC_SUFFIX));
        mrs.add(makeRegistration(BULK_LATENCY_METRIC_PREFIX + P90_METRIC_SUFFIX));
        mrs.add(makeRegistration(BULK_LATENCY_METRIC_PREFIX + P95_METRIC_SUFFIX));
        mrs.add(makeRegistration(BULK_LATENCY_METRIC_PREFIX + P99_METRIC_SUFFIX));

        mrs.add(makeResourceTrackRegistration(INDEX_SHARD_CPU_TIME_METRIC));
        mrs.add(makeResourceTrackRegistration(INDEX_SHARD_CPU_PERCENT_METRIC));
        mrs.add(makeResourceTrackRegistration(INDEX_SHARD_MEMORY_ALLOCATION_METRIC));
        metricRegistrations = Collections.unmodifiableList(mrs);
    }

    private volatile MetricsCollector collector;

    public IndexMetricCollector() {
        collector = new MetricsCollector();
    }

    private static PrometheusMetricRegistration makeRegistration(String metricName) {
        return new PrometheusMetricRegistration(metricName, new String[]{"index", "success"}, metricName);
    }

    private static PrometheusMetricRegistration makeResourceTrackRegistration(String metricName) {
        return new PrometheusMetricRegistration(metricName, new String[]{"index", "shard", "operation"}, metricName);
    }

    public void recordHistogram(PrometheusMetricContext context, long value) {
        HistogramMetric histogram = collector.histograms.computeIfAbsent(context, k -> new HistogramMetric());
        histogram.add(value);
    }

    public void recordCounter(PrometheusMetricContext context, long value) {
        CounterMetric counter = collector.counters.computeIfAbsent(context, k -> new CounterMetric());
        counter.inc(value);
    }

    public void recordSearchLatency(String index, boolean success, long latency) {
        final PrometheusMetricContext context = PrometheusMetricContext.of(SEARCH_LATENCY_METRIC_PREFIX, index, success ? "true" : "false");
        recordHistogram(context, latency);
    }

    public void recordBulkLatency(String index, boolean success, long latency) {
        final PrometheusMetricContext context = PrometheusMetricContext.of(BULK_LATENCY_METRIC_PREFIX, index, success ? "true" : "false");
        recordHistogram(context, latency);
    }

    public void recordIndexShardMemoryResourceMetric(String index, String shard, String op, long value) {
        final PrometheusMetricContext context = PrometheusMetricContext.of(INDEX_SHARD_MEMORY_ALLOCATION_METRIC, index, shard, op);
        recordCounter(context, value);
    }

    public void recordIndexShardCPUResourceMetric(String index, String shard, String op, long value) {
        final PrometheusMetricContext context = PrometheusMetricContext.of(INDEX_SHARD_CPU_TIME_METRIC, index, shard, op);
        recordCounter(context, value);
    }

    public List<PrometheusMetricRegistration> getMetricRegistrations() {
        return metricRegistrations;
    }

    public List<PrometheusMetric> flushMetrics() {
        final MetricsCollector snapshot = collector;
        collector = new MetricsCollector();

        final List<PrometheusMetric> metricList = new ArrayList<>(snapshot.histograms.size() * 6);
        // flush histograms
        for (Map.Entry<PrometheusMetricContext, HistogramMetric> entry : snapshot.histograms.entrySet()) {
            final PrometheusMetricContext context = entry.getKey();
            final Histogram histogram = entry.getValue().getHistogram();
            flushHistogramMetric(context, histogram, metricList);
        }

        // flush counters
        long elapsedTime = System.nanoTime() - snapshot.startTime;
        for (Map.Entry<PrometheusMetricContext, CounterMetric> entry : snapshot.counters.entrySet()) {
            final PrometheusMetricContext context = entry.getKey();
            final CounterMetric counter = entry.getValue();
            metricList.add(new PrometheusMetric(context.name(), context.labelValues(), counter.count()));
            if (context.name().equals(INDEX_SHARD_CPU_TIME_METRIC)) {
                long cpuUsagePercent = counter.count() / (elapsedTime / 10000);
                metricList.add(new PrometheusMetric(INDEX_SHARD_CPU_PERCENT_METRIC, context.labelValues(), cpuUsagePercent));
            }
        }

        return metricList;
    }


    private void flushHistogramMetric(
            final PrometheusMetricContext context,
            final Histogram histogram,
            final List<PrometheusMetric> metricList
    ) {
        BiFunction<String, Double, PrometheusMetric> makeMetric =
                (suffix, value) -> new PrometheusMetric(context.name() + suffix, context.labelValues(), value);
        metricList.add(makeMetric.apply(COUNT_METRIC_SUFFIX, (double) histogram.getCountValue()));
        metricList.add(makeMetric.apply(AVERAGE_METRIC_SUFFIX, histogram.getAverageValue()));
        metricList.add(makeMetric.apply(P50_METRIC_SUFFIX, histogram.getValueAtQuantile(0.50)));
        metricList.add(makeMetric.apply(P90_METRIC_SUFFIX, histogram.getValueAtQuantile(0.90)));
        metricList.add(makeMetric.apply(P95_METRIC_SUFFIX, histogram.getValueAtQuantile(0.95)));
        metricList.add(makeMetric.apply(P99_METRIC_SUFFIX, histogram.getValueAtQuantile(0.99)));
    }

    private static class MetricsCollector {
        long startTime = System.nanoTime();
        ConcurrentMap<PrometheusMetricContext, HistogramMetric> histograms =
                ConcurrentCollections.newConcurrentMapWithAggressiveConcurrency();
        // add other types of metrics here, like counter
        ConcurrentMap<PrometheusMetricContext, CounterMetric> counters =
                ConcurrentCollections.newConcurrentMapWithAggressiveConcurrency();
    }
}