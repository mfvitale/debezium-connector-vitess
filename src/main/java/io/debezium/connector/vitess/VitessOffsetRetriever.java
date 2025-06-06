/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.debezium.connector.vitess;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.apache.kafka.connect.storage.OffsetStorageReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.debezium.connector.vitess.pipeline.txmetadata.VitessOrderedTransactionMetadataFactory;

/**
 * Retrieves values from offsets, specifically for retrieving the previous VGTID and shard epoch
 * values.
 */
public class VitessOffsetRetriever {

    private static final Logger LOGGER = LoggerFactory.getLogger(VitessOffsetRetriever.class);

    private final int numTasks;
    private final int gen;
    private boolean expectsOffset;
    private final VitessConnectorConfig config;
    private final OffsetStorageReader reader;

    public VitessOffsetRetriever(VitessConnectorConfig config, int numTasks, int gen, boolean expectsOffset, OffsetStorageReader reader) {
        this.config = config;
        this.numTasks = numTasks;
        this.gen = gen;
        this.expectsOffset = expectsOffset;
        this.reader = reader;
    }

    public static boolean isShardEpochMapEnabled(VitessConnectorConfig config) {
        return config.getTransactionMetadataFactory() instanceof VitessOrderedTransactionMetadataFactory;
    }

    public void setExpectsOffset(boolean expectsOffset) {
        this.expectsOffset = expectsOffset;
    }

    public Map<String, String> getGtidPerShard() {
        return (Map) getValuePerShardFromStorage(OffsetValueType.GTID);
    }

    public Map<String, Long> getEpochPerShard() {
        return (Map) getValuePerShardFromStorage(OffsetValueType.EPOCH);
    }

    public Map<String, ?> getValuePerShardFromStorage(OffsetValueType valueType) {
        String key = valueType.name;
        Function<String, Map<String, Object>> valueReader = valueType.parserFunction;
        return getValuePerShardFromStorage(
                key,
                valueReader);
    }

    public Map<String, Object> getValuePerShardFromStorage(String key, Function<String, Map<String, Object>> valueReader) {
        if (gen < 0) {
            return null;
        }
        final Map<String, Object> valuesPerShard = new HashMap<>();
        for (int i = 0; i < numTasks; i++) {
            String taskKey = VitessConnector.getTaskKeyName(i, numTasks, gen);
            VitessPartition par = new VitessPartition(config.getLogicalName(), taskKey);
            Map<String, Object> offset = reader.offset(par.getSourcePartition());
            if (offset == null && gen == 0) {
                LOGGER.info("No previous offset for partition: {}, fall back to only server key", par);
                par = new VitessPartition(config.getLogicalName(), null);
                offset = reader.offset(par.getSourcePartition());
            }
            if (offset == null) {
                if (expectsOffset) {
                    throw new IllegalArgumentException(String.format("No offset found for %s", par));
                }
                else {
                    LOGGER.warn("No offset found for task key: {}", taskKey);
                    continue;
                }
            }
            final String stringValue = (String) offset.get(key);
            Objects.requireNonNull(stringValue, String.format("Missing %s from %s", key, offset));
            Map<String, Object> shardToValue = valueReader.apply(stringValue);
            valuesPerShard.putAll(shardToValue);

        }
        return valuesPerShard;
    }

}
