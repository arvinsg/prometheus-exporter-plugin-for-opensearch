package org.opensearch.action.support;

import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Settings;
import org.opensearch.metrics.IndexMetricCollector;
import org.opensearch.transport.TransportInterceptor;
import org.opensearch.transport.TransportRequest;
import org.opensearch.transport.TransportRequestHandler;

public class MetricTransportInterceptor implements TransportInterceptor {


    private volatile boolean taskResourceTrackEnabled;
    private final IndexMetricCollector metricCollector;

    public static final Setting<Boolean> PROMETHEUS_TASK_RESOURCE_TRACK_ENABLED_SETTINGS = Setting.boolSetting(
            "prometheus.task_resource_track.enabled",
            true,
            Setting.Property.Dynamic,
            Setting.Property.NodeScope
    );


    public MetricTransportInterceptor(Settings settings, ClusterSettings clusterSettings, IndexMetricCollector metricCollector) {
        this.metricCollector = metricCollector;
        setTaskResourceTrackEnabled(PROMETHEUS_TASK_RESOURCE_TRACK_ENABLED_SETTINGS.get(settings));
        clusterSettings.addSettingsUpdateConsumer(PROMETHEUS_TASK_RESOURCE_TRACK_ENABLED_SETTINGS, this::setTaskResourceTrackEnabled);
    }


    private void setTaskResourceTrackEnabled(boolean enabled) {
        this.taskResourceTrackEnabled = enabled;
    }

    public boolean getTaskResourceTrackEnabled() {
        return this.taskResourceTrackEnabled;
    }


    public IndexMetricCollector getIndexMetricCollector() {
        return this.metricCollector;
    }

    @Override
    public <T extends TransportRequest> TransportRequestHandler<T> interceptHandler(String action, String executor, boolean forceExecution, TransportRequestHandler<T> actualHandler) {
        return new MetricTransportRequestHandler<>(actualHandler, this);
    }
}

