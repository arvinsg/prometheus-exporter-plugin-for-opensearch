/*
 * Copyright [2021] [Lukas Vlcek]
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

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.http.util.EntityUtils;
import org.opensearch.action.admin.cluster.node.info.NodeInfo;
import org.opensearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.opensearch.action.admin.cluster.node.info.PluginsAndModules;
import org.opensearch.action.admin.indices.create.CreateIndexRequest;
import org.opensearch.action.search.MultiSearchRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.RestClient;
import org.opensearch.common.settings.Settings;
import org.opensearch.plugins.Plugin;
import org.opensearch.test.OpenSearchIntegTestCase;
import org.opensearch.common.xcontent.XContentType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import org.opensearch.test.hamcrest.OpenSearchAssertions;

import static org.opensearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.opensearch.client.Requests.flushRequest;
import static org.opensearch.test.hamcrest.OpenSearchAssertions.assertHitCount;

@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE, numDataNodes = 2, numClientNodes = 0, supportsDedicatedMasters = false)
public class PrometheusPluginIT extends OpenSearchIntegTestCase {

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return Arrays.asList(PrometheusExporterPlugin.class);
    }

    /**
     * Plugin must be installed on every cluster node.
     */
    public void testPluginInstalled() {
        NodesInfoResponse response = client().admin().cluster().prepareNodesInfo().clear().all().get();
        assertEquals(0, response.failures().size());
        assertFalse(response.getNodes().isEmpty());
        for (NodeInfo ni : response.getNodes()) {
            assertNotNull(ni.getInfo(PluginsAndModules.class));
            assertEquals(
                    1,
                    ni.getInfo(PluginsAndModules.class).getPluginInfos().stream().filter(
                            pluginInfo -> pluginInfo.getClassname().endsWith("PrometheusExporterPlugin")
                    ).count()
            );
        }
    }

    public void testPrometheusClientResponse() throws IOException {
        RestClient rc = getRestClient();
        logClusterState();
        Response response = rc.performRequest(new Request("GET", "_prometheus/metrics"));
        assertEquals(200, response.getStatusLine().getStatusCode());
        assertEquals("text/plain; charset=UTF-8", response.getEntity().getContentType().getValue());
        String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        assertTrue(body.startsWith("# HELP"));
    }


    public void testIndexMetric() throws IOException {
        final String indexName = "test-index";
        CreateIndexRequest request = new CreateIndexRequest(
                indexName,
                Settings.builder()
                        .put("index.number_of_shards", 1)
                        .put("index.number_of_replicas", 0)
                        .build()
        );
        OpenSearchAssertions.assertAcked(client().admin().indices().create(request).actionGet());

        int numOfDocs = 100;
        for (int i = 0; i < numOfDocs; i++) {
            client().prepareIndex(indexName)
                    .setSource("{\"foo\" : \"bar\", \"i\" : " + i + "}", XContentType.JSON)
                    .setRefreshPolicy(IMMEDIATE).get();
            client().search(new SearchRequest(indexName)).actionGet();
        }


        client().admin().indices().flush(flushRequest(indexName)).actionGet();

        assertHitCount(client().prepareSearch(indexName).setSize(0).get(), numOfDocs);

        client().multiSearch(new MultiSearchRequest().add(new SearchRequest(indexName))).actionGet();


        SearchResponse sresponse = client().search(new SearchRequest(indexName)).actionGet();
        System.out.println(sresponse.getHits().getTotalHits());


        RestClient rc = getRestClient();
        Response response = rc.performRequest(new Request("GET", "_prometheus/metrics"));
        assertEquals(200, response.getStatusLine().getStatusCode());
        String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        Map<String, Map<String, Double>> metrics = parseMetric(body);

        for (String metric : metrics.keySet()) {
            System.out.println(metric);
        }

        response = rc.performRequest(new Request("GET", "_cluster/settings"));
        body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        System.out.println(body);

        // assertTrue(metrics.containsKey(SEARCH_LATENCY_METRIC_PREFIX + COUNT_METRIC_SUFFIX));
    }

    public Map<String, Map<String, Double>> parseMetric(String body) {
        Pattern metricPattern = Pattern.compile("^(\\w+)\\{([^}]+)\\} (\\d+\\.+\\d+)$");
        Map<String, Map<String, Double>> parsedMetrics = new HashMap<>();
        for (String line : body.split("\n")) {
            if (line.startsWith("#")) {
                continue;
            }

            Matcher matcher = metricPattern.matcher(line);
            if (matcher.matches()) {
                String metricName = matcher.group(1);
                String labels = matcher.group(2);
                Double value = Double.parseDouble(matcher.group(3));
                Map<String, String> labelMap = new HashMap<>();
                for (String label : labels.split(",")) {
                    String[] parts = label.split("=");
                    labelMap.put(parts[0].trim(), parts[1].replaceAll("\"", "").trim());
                }

                parsedMetrics.computeIfAbsent(metricName, k -> new HashMap<>()).put(labels, value);
            }
        }

        return parsedMetrics;
    }
}
