package org.opensearch.action.support;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.plaf.metal.MetalPopupMenuSeparatorUI;
import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionResponse;
import org.opensearch.action.bulk.BulkAction;
import org.opensearch.action.get.GetAction;
import org.opensearch.action.get.MultiGetAction;
import org.opensearch.action.get.MultiGetShardRequest;
import org.opensearch.action.search.MultiSearchAction;
import org.opensearch.action.search.SearchProgressListener;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchShard;
import org.opensearch.action.search.SearchShardTask;
import org.opensearch.action.search.SearchTask;
import org.opensearch.action.support.replication.ReplicationTask;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Settings;
import org.opensearch.metrics.CoordinatorIndexMetricCollector;
import org.opensearch.tasks.Task;

public class MetricsActionFilter implements ActionFilter {
    public static final Setting<Boolean> PROMETHEUS_COORDINATOR_METRICS_ENABLED_SETTINGS = Setting.boolSetting(
            "prometheus.coordinator_metrics.enabled",
            true,
            Setting.Property.Dynamic,
            Setting.Property.NodeScope
    );

    public static final Setting<Boolean> PROMETHEUS_TASK_RESOURCE_TRACK_ENABLED_SETTINGS = Setting.boolSetting(
            "prometheus.task_resource_track.enabled",
            true,
            Setting.Property.Dynamic,
            Setting.Property.NodeScope
    );

    private final CoordinatorIndexMetricCollector metricCollector;
    private volatile boolean coordinatorMetricsEnabled;
    private volatile boolean taskResourceTrackEnabled;

    public MetricsActionFilter(Settings settings, ClusterSettings clusterSettings, CoordinatorIndexMetricCollector metricCollector) {
        this.metricCollector = metricCollector;
        setCoordinatorMetricsEnabled(PROMETHEUS_COORDINATOR_METRICS_ENABLED_SETTINGS.get(settings));
        setTaskResourceTrackEnabled(PROMETHEUS_TASK_RESOURCE_TRACK_ENABLED_SETTINGS.get(settings));
        clusterSettings.addSettingsUpdateConsumer(PROMETHEUS_COORDINATOR_METRICS_ENABLED_SETTINGS, this::setCoordinatorMetricsEnabled);
        clusterSettings.addSettingsUpdateConsumer(PROMETHEUS_TASK_RESOURCE_TRACK_ENABLED_SETTINGS, this::setTaskResourceTrackEnabled);
    }

    private void setCoordinatorMetricsEnabled(boolean enabled) {
        this.coordinatorMetricsEnabled = enabled;
    }

    private void setTaskResourceTrackEnabled(boolean enabled) {
        this.taskResourceTrackEnabled = enabled;
    }

    private boolean getCoordinatorMetricsEnable() {
        return coordinatorMetricsEnabled;
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public <Request extends ActionRequest, Response extends ActionResponse> void apply(
            Task task,
            String action,
            Request request,
            ActionListener<Response> listener,
            ActionFilterChain<Request, Response> chain
    ) {
        if (getCoordinatorMetricsEnable() == false) {
            chain.proceed(task, action, request, listener);
            return;
        }

        if (shouldRecordMetric(task, action, request)) {
            chain.proceed(
                    task,
                    action,
                    request,
                    new MetricsActionListener<>(listener, task, action, metricCollector, request, taskResourceTrackEnabled)
            );
        } else {
            chain.proceed(task, action, request, listener);
        }
    }


    private boolean shouldRecordMetric(Task task, String action, ActionRequest request) {
        if (task instanceof SearchTask || task instanceof SearchShardTask || task instanceof ReplicationTask) {
            return true;
        }

        if (request instanceof MultiGetShardRequest) {
            return true;
        }

        return action.equals(MultiSearchAction.NAME) || action.equals(BulkAction.NAME) ||
                action.equals(GetAction.NAME) || action.equals(MultiGetAction.NAME);
    }

    public static class MetricsSearchProgressListener extends SearchProgressListener {
        private List<SearchShard> searchShards;

        @Override
        protected final void onListShards(
                List<SearchShard> shards,
                List<SearchShard> skippedShards,
                SearchResponse.Clusters clusters,
                boolean fetchPhase
        ) {
            this.searchShards = shards;
            super.onListShards(shards, skippedShards, clusters, fetchPhase);
        }

        public Set<String> getSearchIndices() {
            if (searchShards == null) {
                return Collections.emptySet();
            }
            return searchShards.stream().map(shard -> shard.getShardId().getIndexName()).collect(Collectors.toSet());
        }
    }
}
