package org.opensearch.metrics;

import com.datadoghq.sketch.ddsketch.DDSketch;
import com.datadoghq.sketch.ddsketch.mapping.CubicallyInterpolatedMapping;
import com.datadoghq.sketch.ddsketch.store.UnboundedSizeDenseStore;

public class DDSketchHistogram extends DDSketch implements Histogram {
    private static final double RELATIVE_ACCURACY = 0.01;
    private long count = 0;
    private long sum = 0;

    public DDSketchHistogram() {
        super(new CubicallyInterpolatedMapping(RELATIVE_ACCURACY), UnboundedSizeDenseStore::new);
    }

    @Override
    public void add(long value) {
        count++;
        sum += value;
        super.accept(value);
    }

    @Override
    public double getValueAtQuantile(double quantile) {
        if (count == 0) {
            return 0;
        }
        return super.getValueAtQuantile(quantile);
    }

    @Override
    public double[] getValuesAtQuantiles(double[] quantiles) {
        if (count == 0) {
            return new double[quantiles.length];
        }
        return super.getValuesAtQuantiles(quantiles);
    }

    @Override
    public long getCountValue() {
        return count;
    }

    @Override
    public double getAverageValue() {
        if (count == 0) {
            return 0;
        }
        return (double) sum / count;
    }
}

