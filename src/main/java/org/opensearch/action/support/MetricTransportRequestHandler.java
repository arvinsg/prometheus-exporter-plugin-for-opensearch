package org.opensearch.action.support;

import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.metrics.IndexMetricCollector;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportChannel;
import org.opensearch.transport.TransportRequest;
import org.opensearch.transport.TransportRequestHandler;
import org.opensearch.transport.TransportResponse;
import org.opensearch.tasks.TaskResourceUsage;


import static org.opensearch.action.support.MetricsUtils.extractIndices;
import static org.opensearch.action.support.MetricsUtils.extractOperation;
import static org.opensearch.action.support.MetricsUtils.extractShard;


public class MetricTransportRequestHandler<T extends TransportRequest> implements TransportRequestHandler<T> {
    private static final Logger logger = LogManager.getLogger(MetricTransportRequestHandler.class);
    private final TransportRequestHandler<T> actualHandler;
    private final MetricTransportInterceptor interceptor;


    public MetricTransportRequestHandler(TransportRequestHandler<T> actualHandler, MetricTransportInterceptor interceptor) {
        this.actualHandler = actualHandler;
        this.interceptor = interceptor;
    }

    @Override
    public void messageReceived(T request, TransportChannel channel, Task task) throws Exception {
        if (!interceptor.getTaskResourceTrackEnabled()) {
            actualHandler.messageReceived(request, channel, task);
            return;
        }

        TransportChannel wrapperChannel = new TransportChannelWrapper(request, channel, task, interceptor.getIndexMetricCollector());
        actualHandler.messageReceived(request, wrapperChannel, task);
    }

    static class TransportChannelWrapper implements TransportChannel {
        private final TransportChannel channel;
        private final Task task;
        private final TransportRequest request;
        private final IndexMetricCollector collector;

        TransportChannelWrapper(TransportRequest request, TransportChannel channel, Task task, IndexMetricCollector collector) {
            this.channel = channel;
            this.task = task;
            this.request = request;
            this.collector = collector;
        }

        @Override
        public String getProfileName() {
            return channel.getProfileName();
        }

        @Override
        public String getChannelType() {
            return channel.getChannelType();
        }

        @Override
        public void sendResponse(TransportResponse transportResponse) throws IOException {
            channel.sendResponse(transportResponse);
            recordMetrics();
        }

        @Override
        public void sendResponse(Exception e) throws IOException {
            channel.sendResponse(e);
            recordMetrics();
        }


        private void recordMetrics() {
            if (task.supportsResourceTracking()) {
                String index = extractIndices(request);
                String shard = extractShard(request);
                String op = extractOperation(request);

                TaskResourceUsage usage = task.getTotalResourceStats();
                if (usage.getCpuTimeInNanos() > 0) {
                    collector.recordIndexShardCPUResourceMetric(index, shard, op, usage.getCpuTimeInNanos());
                }
                if (usage.getMemoryInBytes() > 0) {
                    collector.recordIndexShardMemoryResourceMetric(index, shard, op, usage.getMemoryInBytes());
                }
                logger.debug("record resource metric, index: {}, shard: {}, operation: {}, CpuTimeInNanos: {}, MemoryInBytes: {}",
                        index, shard, op, usage.getCpuTimeInNanos(), usage.getMemoryInBytes());
            }
        }
    }
}
