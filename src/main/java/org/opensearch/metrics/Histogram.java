package org.opensearch.metrics;

public interface Histogram {
    void add(long value);

    double getValueAtQuantile(double quantile);

    double[] getValuesAtQuantiles(double[] quantiles);

    long getCountValue();

    double getAverageValue();

    static Histogram unmodifiableHistogram(Histogram histogram) {
        return new UnmodifiableHistogram(histogram);
    }

    class UnmodifiableHistogram implements Histogram {
        private final Histogram delegate;

        public UnmodifiableHistogram(Histogram delegate) {
            this.delegate = delegate;
        }

        @Override
        public void add(long value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public double getValueAtQuantile(double quantile) {
            return delegate.getValueAtQuantile(quantile);
        }

        @Override
        public double[] getValuesAtQuantiles(double[] quantiles) {
            return delegate.getValuesAtQuantiles(quantiles);
        }

        @Override
        public long getCountValue() {
            return delegate.getCountValue();
        }

        @Override
        public double getAverageValue() {
            return delegate.getAverageValue();
        }
    }
}