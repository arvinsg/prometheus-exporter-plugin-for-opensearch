package org.opensearch.action.support;

import com.google.common.collect.Ordering;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.IndicesRequest;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkShardRequest;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.MultiGetRequest;
import org.opensearch.action.get.MultiGetShardRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.search.MultiSearchRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchScrollRequest;
import org.opensearch.action.support.replication.TransportReplicationAction;
import org.opensearch.search.fetch.ShardFetchSearchRequest;
import org.opensearch.search.internal.InternalScrollSearchRequest;
import org.opensearch.search.internal.ShardSearchRequest;
import org.opensearch.transport.TransportRequest;

public class MetricsUtils {
    private static final Logger logger = LogManager.getLogger(MetricTransportRequestHandler.class);

    private static final String DEFAULT_EMPTY = "-";
    private static final String DEFAULT_UNKNOWN = "_unknown";
    private static final String DEFAULT_SCROLL = "_scroll";
    private static final String DEFAULT_MSEARCH = "_msearch";
    private static final String ETC_INDICATOR = "_etc";
    private static final String DELIMITER = "/";
    private static final int MAX_INDEX_COUNT = 3;

    private MetricsUtils() {
    }

    static String extractIndices(TransportRequest request) {
        String index = DEFAULT_UNKNOWN;
        if (request instanceof IndicesRequest) {
            index = joinIndices(((IndicesRequest) request).indices());
        } else if (request instanceof BulkRequest) {
            Set<String> indices = ((BulkRequest) request).getIndices();
            if (indices != null && !indices.isEmpty()) {
                index = joinIndices(indices.toArray(new String[0]));
            }
        } else if (request instanceof MultiSearchRequest) {
            index = DEFAULT_MSEARCH;
        } else if (request instanceof SearchScrollRequest) {
            index = DEFAULT_SCROLL;
        } else if (request instanceof MultiGetRequest) {
            Set<String> indices = new HashSet<>();
            for (MultiGetRequest.Item item : ((MultiGetRequest) request).getItems()) {
                indices.add(item.index());
            }

            if (!indices.isEmpty()) {
                index = joinIndices(indices.toArray(new String[0]));
            }
        } else if (request instanceof TransportReplicationAction.ConcreteShardRequest) {
            return extractIndices(((TransportReplicationAction.ConcreteShardRequest<?>) request).getRequest());
        } else {
            logger.debug("unkonwn index, request: {}", request.getClass());
        }

        return patchMetrics(index);
    }

    static String extractShard(TransportRequest request) {
        String shard = DEFAULT_EMPTY;
        if (request instanceof BulkShardRequest) {
            shard = ((BulkShardRequest) request).shardId().getId() + "";
        } else if (request instanceof MultiGetShardRequest) {
            shard = ((MultiGetShardRequest) request).shardId() + "";
        } else if (request instanceof ShardSearchRequest) {
            shard = ((ShardSearchRequest) request).shardId().getId() + "";
        } else if (request instanceof ShardFetchSearchRequest) {
            shard = ((ShardFetchSearchRequest) request).getShardSearchRequest().shardId().getId() + "";
        } else if (request instanceof TransportReplicationAction.ConcreteShardRequest) {
            return extractShard(((TransportReplicationAction.ConcreteShardRequest<?>) request).getRequest());
        } else {
            logger.debug("unkonown shard, request:{}", request.getClass());
        }
        return shard;
    }

    static String extractOperation(TransportRequest request) {
        if (request instanceof IndexRequest || request instanceof BulkRequest || request instanceof BulkShardRequest) {
            return "index";
        } else if (request instanceof GetRequest || request instanceof MultiGetRequest ||
                request instanceof MultiGetShardRequest) {
            return "get";
        } else if (request instanceof SearchRequest || request instanceof ShardSearchRequest || request instanceof ShardFetchSearchRequest ||
                request instanceof MultiSearchRequest || request instanceof SearchScrollRequest || request instanceof InternalScrollSearchRequest) {
            return "search";
        } else if (request instanceof TransportReplicationAction.ConcreteShardRequest) {
            return extractOperation(((TransportReplicationAction.ConcreteShardRequest<?>) request).getRequest());
        } else {
            logger.debug("unknow operation type, request: {}", request.getClass());
        }

        return DEFAULT_UNKNOWN;
    }

    private static String joinIndices(String[] indices) {
        if (null == indices || 0 == indices.length) {
            return DEFAULT_EMPTY;
        }
        if (1 == indices.length) {
            return getOrDefault(indices[0], DEFAULT_UNKNOWN);
        }
        Arrays.sort(indices, Ordering.natural().nullsLast());
        boolean flag = false;
        String prevIdx = "";
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String idx : indices) {
            // sort first and put null last
            if (null == idx) {
                break;
            }
            if (idx.isEmpty() || prevIdx.equals(idx)) {
                continue;
            }
            if (flag) {
                sb.append(DELIMITER);
            } else {
                flag = true;
            }
            sb.append(idx);
            if (count++ >= MAX_INDEX_COUNT) {
                sb.append(DELIMITER).append(ETC_INDICATOR);
                break;
            }
            prevIdx = idx;
        }
        return getOrDefault(sb.toString(), DEFAULT_UNKNOWN);
    }

    private static String patchMetrics(String input) {
        // metric does not allow */$
        if (input.contains("*")) {
            input = input.replaceAll("\\*", "__any");
        }
        return input;
    }

    private static String getOrDefault(String s, String dft) {
        if (s == null || s.isEmpty()) {
            return dft;
        }
        return s;
    }
}
