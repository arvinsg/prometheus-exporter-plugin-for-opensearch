package org.opensearch.action.support;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionResponse;
import org.opensearch.action.bulk.BulkAction;
import org.opensearch.action.search.MultiSearchAction;
import org.opensearch.action.search.SearchProgressListener;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchShard;
import org.opensearch.action.search.SearchTask;
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

    private final CoordinatorIndexMetricCollector metricCollector;
    private volatile boolean coordinatorMetricsEnabled;

    public MetricsActionFilter(Settings settings, ClusterSettings clusterSettings, CoordinatorIndexMetricCollector metricCollector) {
        this.metricCollector = metricCollector;
        setCoordinatorMetricsEnabled(PROMETHEUS_COORDINATOR_METRICS_ENABLED_SETTINGS.get(settings));
        clusterSettings.addSettingsUpdateConsumer(PROMETHEUS_COORDINATOR_METRICS_ENABLED_SETTINGS, this::setCoordinatorMetricsEnabled);
    }

    private void setCoordinatorMetricsEnabled(boolean enabled) {
        this.coordinatorMetricsEnabled = enabled;
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
        if (task instanceof SearchTask || action.equals(MultiSearchAction.NAME) || action.equals(BulkAction.NAME)) {

            chain.proceed(
                    task,
                    action,
                    request,
                    new MetricsActionListener<>(listener, task, metricCollector, request)
            );
        } else {
            chain.proceed(task, action, request, listener);
        }
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
