package org.opensearch.action.support;

import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.search.SearchTask;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.metrics.IndexMetricCollector;
import org.opensearch.tasks.Task;

import static org.opensearch.action.support.MetricsUtils.extractIndices;


public class MetricsActionListener<Request extends ActionRequest, Response> implements ActionListener<Response> {
    private final ActionListener<Response> delegate;

    private final Task task;

    private final IndexMetricCollector metricCollector;

    private final Request request;

    public MetricsActionListener(
            ActionListener<Response> delegate,
            Task task,
            IndexMetricCollector metricCollector,
            Request request
    ) {
        this.delegate = delegate;
        this.task = task;
        this.metricCollector = metricCollector;
        this.request = request;
    }

    @Override
    public void onResponse(Response response) {
        recordMetrics(true);
        delegate.onResponse(response);
    }

    @Override
    public void onFailure(Exception e) {
        recordMetrics(false);
        delegate.onFailure(e);
    }

    private void recordMetrics(boolean success) {
        long elapsedTime = TimeValue.nsecToMSec(System.nanoTime() - task.getStartTimeNanos());

        String targetLogicIndex = extractIndices(request);
        if (task instanceof SearchTask) {
            metricCollector.recordSearchLatency(targetLogicIndex, success, elapsedTime);
        } else {
            metricCollector.recordBulkLatency(targetLogicIndex, success, elapsedTime);
        }
    }
}