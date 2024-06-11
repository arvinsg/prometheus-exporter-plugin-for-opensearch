/*
 * Copyright [2016] [Vincent VAN HOLLEBEKE]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.opensearch.plugin.prometheus;

import static java.util.Collections.singletonList;
import static org.opensearch.action.support.MetricsActionFilter.PROMETHEUS_COORDINATOR_METRICS_ENABLED_SETTINGS;

import java.util.Collection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.compuscene.metrics.prometheus.PrometheusSettings;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionResponse;
import org.opensearch.action.NodePrometheusMetricsAction;
import org.opensearch.action.TransportNodePrometheusMetricsAction;
import org.opensearch.action.support.ActionFilter;
import org.opensearch.action.support.MetricsActionFilter;
import org.opensearch.client.Client;
import org.opensearch.cluster.metadata.IndexNameExpressionResolver;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.SetOnce;
import org.opensearch.common.io.stream.NamedWriteableRegistry;
import org.opensearch.common.settings.*;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.env.Environment;
import org.opensearch.env.NodeEnvironment;
import org.opensearch.metrics.CoordinatorIndexMetricCollector;
import org.opensearch.plugins.ActionPlugin;
import org.opensearch.plugins.Plugin;
import org.opensearch.repositories.RepositoriesService;
import org.opensearch.rest.RestController;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.prometheus.RestPrometheusMetricsAction;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import org.opensearch.script.ScriptService;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.watcher.ResourceWatcherService;

/**
 * Prometheus Exporter plugin main class.
 */
public class PrometheusExporterPlugin extends Plugin implements ActionPlugin {
    private static final Logger logger = LogManager.getLogger(PrometheusExporterPlugin.class);
    private final SetOnce<MetricsActionFilter> metricsActionFilter = new SetOnce<>();
    private final CoordinatorIndexMetricCollector coordinatorIndexMetricCollector = new CoordinatorIndexMetricCollector();

    /**
     * A constructor.
     */
    public PrometheusExporterPlugin() {
        logger.info("starting Prometheus exporter plugin");
    }

    @Override
    public List<ActionHandler<? extends ActionRequest, ? extends ActionResponse>> getActions() {
        return singletonList(
                new ActionHandler<>(NodePrometheusMetricsAction.INSTANCE, TransportNodePrometheusMetricsAction.class)
        );
    }

    @Override
    public Collection<Object> createComponents(
            Client client,
            ClusterService clusterService,
            ThreadPool threadPool,
            ResourceWatcherService resourceWatcherService,
            ScriptService scriptService,
            NamedXContentRegistry xContentRegistry,
            Environment environment,
            NodeEnvironment nodeEnvironment,
            NamedWriteableRegistry namedWriteableRegistry,
            IndexNameExpressionResolver indexNameExpressionResolver,
            Supplier<RepositoriesService> repositoriesServiceSupplier) {
        this.metricsActionFilter.set(
                new MetricsActionFilter(clusterService.getSettings(), clusterService.getClusterSettings(), coordinatorIndexMetricCollector)
        );

        return Collections.emptyList();
    }

    @Override
    public List<ActionFilter> getActionFilters() {
        return singletonList(metricsActionFilter.get());
    }

    @Override
    public List<RestHandler> getRestHandlers(Settings settings, RestController restController, ClusterSettings clusterSettings,
                                             IndexScopedSettings indexScopedSettings, SettingsFilter settingsFilter,
                                             IndexNameExpressionResolver indexNameExpressionResolver,
                                             Supplier<DiscoveryNodes> nodesInCluster) {
        return singletonList(
                new RestPrometheusMetricsAction(settings, clusterSettings, coordinatorIndexMetricCollector)
        );
    }

    @Override
    public List<Setting<?>> getSettings() {
        List<Setting<?>> settings = Arrays.asList(
                PrometheusSettings.PROMETHEUS_CLUSTER_SETTINGS,
                PrometheusSettings.PROMETHEUS_INDICES,
                PrometheusSettings.PROMETHEUS_NODES_FILTER,
                PrometheusSettings.PROMETHEUS_SELECTED_INDICES,
                PrometheusSettings.PROMETHEUS_SELECTED_OPTION,
                RestPrometheusMetricsAction.METRIC_PREFIX,
                PROMETHEUS_COORDINATOR_METRICS_ENABLED_SETTINGS
        );
        return Collections.unmodifiableList(settings);
    }
}
