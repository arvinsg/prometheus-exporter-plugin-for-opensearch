package org.opensearch.action.support;

import com.google.common.collect.Ordering;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.opensearch.action.ActionRequest;
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
import org.opensearch.search.internal.InternalScrollSearchRequest;
import org.opensearch.search.internal.ShardSearchRequest;

public class MetricsUtils {
    private static final String DEFAULT_EMPTY = "-";
    private static final String DEFAULT_UNKNOWN = "_unknown";
    private static final String DEFAULT_SCROLL = "_scroll";
    private static final String DEFAULT_MSEARCH = "_msearch";
    private static final String ETC_INDICATOR = "_etc";
    private static final String DELIMITER = "/";
    private static final int MAX_INDEX_COUNT = 3;

    private MetricsUtils() {
    }


    // TODO check ShardSearchRequest, ShardFetchSearchRequest, InternalScrollSearchRequest
    static String extractIndices(ActionRequest request) {
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
        }

        return patchMetrics(index);
    }

    // TODO check search ShardSearchRequest
    static String extractShard(ActionRequest request) {
        String shard = DEFAULT_EMPTY;
        if (request instanceof MultiGetShardRequest) {
            shard = ((MultiGetShardRequest) request).shardId() + "";
        } else if (request instanceof BulkShardRequest) {
            shard = ((BulkShardRequest) request).shardId().getId() + "";
        }
        return shard;
    }


    // TODO check search  ShardSearchRequest, ShardFetchSearchRequest, InternalScrollSearchRequest
    static String extractOperation(ActionRequest request) {

        if (request instanceof IndexRequest || request instanceof BulkRequest || request instanceof BulkShardRequest) {
            return "index";
        } else if (request instanceof GetRequest || request instanceof MultiGetRequest ||
                request instanceof MultiGetShardRequest) {
            return "get";
        } else if (request instanceof SearchRequest || request instanceof MultiSearchRequest || request instanceof SearchScrollRequest) {
            return "search";
        }

        return DEFAULT_EMPTY;
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
