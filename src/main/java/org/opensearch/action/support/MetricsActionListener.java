package org.opensearch.action.support;

import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.bulk.BulkAction;
import org.opensearch.action.search.MultiSearchAction;
import org.opensearch.action.search.SearchTask;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.metrics.CoordinatorIndexMetricCollector;
import org.opensearch.tasks.Task;
import org.opensearch.tasks.TaskResourceUsage;

import static org.opensearch.action.support.MetricsUtils.extractIndices;
import static org.opensearch.action.support.MetricsUtils.extractOperation;
import static org.opensearch.action.support.MetricsUtils.extractShard;

public class MetricsActionListener<Request extends ActionRequest, Response> implements ActionListener<Response> {
    private final ActionListener<Response> delegate;

    private final Task task;

    private final CoordinatorIndexMetricCollector metricCollector;

    private final Request request;

    private final String action;

    private final boolean enableTrackTaskResource;

    public MetricsActionListener(
            ActionListener<Response> delegate,
            Task task,
            String action,
            CoordinatorIndexMetricCollector metricCollector,
            Request request,
            boolean enable
    ) {
        this.delegate = delegate;
        this.task = task;
        this.action = action;
        this.metricCollector = metricCollector;
        this.request = request;
        this.enableTrackTaskResource = enable;
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
        String targetLogicIndex = extractIndices(request);
        long elapsedTime = TimeValue.nsecToMSec(System.nanoTime() - task.getStartTimeNanos());

        if (task.supportsResourceTracking() && this.enableTrackTaskResource) {
            String shard = extractShard(request);
            String op = extractOperation(request);
            TaskResourceUsage usage = task.getTotalResourceStats();
            // TODO remove
            System.out.println("----------tracking----------");
            System.out.println("action: " + this.action + ", desc: " + task.getDescription() + ", cpu: " + usage.getCpuTimeInNanos() + ", mem:" + usage.getMemoryInBytes());
            System.out.println("task , id: " + task.getId() + ", action: " + task.getAction() + ", type: " + task.getType() + ", parent id: " + task.getParentTaskId());
            System.out.println("index: " + targetLogicIndex + ", shard: " + shard + ", op: " + op);
            System.out.println("request: " + request.getClass());
            System.out.println("----------------------------");

            if (elapsedTime > 0) {
                metricCollector.recordIndexShardCPUResourceMetric(targetLogicIndex, shard, op, usage.getCpuTimeInNanos() / elapsedTime);
            }
            metricCollector.recordIndexShardMemoryResourceMetric(targetLogicIndex, shard, op, usage.getMemoryInBytes());
        }

        if (task instanceof SearchTask) {
            metricCollector.recordSearchLatency(targetLogicIndex, success, elapsedTime);
        } else if (action.equals(MultiSearchAction.NAME) || action.equals(BulkAction.NAME)) {
            metricCollector.recordBulkLatency(targetLogicIndex, success, elapsedTime);
        }
    }
}