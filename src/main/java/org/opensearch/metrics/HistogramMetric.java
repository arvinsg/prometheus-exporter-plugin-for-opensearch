package org.opensearch.metrics;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.opensearch.common.metrics.Metric;

public class HistogramMetric implements Metric {
    private Histogram histogram = new DDSketchHistogram();
    private Lock lock = new ReentrantLock();

    public void add(long value) {
        lock.lock();
        try {
            histogram.add(value);
        } finally {
            lock.unlock();
        }
    }

    public Histogram getHistogram() {
        return Histogram.unmodifiableHistogram(histogram);
    }

    public Histogram flush() {
        lock.lock();
        try {
            Histogram snapshot = histogram;
            histogram = new DDSketchHistogram();
            return Histogram.unmodifiableHistogram(snapshot);
        } finally {
            lock.unlock();
        }
    }
}
