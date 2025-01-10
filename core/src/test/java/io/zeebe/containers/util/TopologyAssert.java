/*
 * Copyright © 2022 camunda services GmbH (info@camunda.com)
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
 */
package io.zeebe.containers.util;

import io.camunda.client.api.response.BrokerInfo;
import io.camunda.client.api.response.PartitionInfo;
import io.camunda.client.api.response.Topology;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.assertj.core.api.AbstractObjectAssert;

/** Convenience class to assert certain properties of a Zeebe cluster {@link Topology}. */
@SuppressWarnings("UnusedReturnValue")
public final class TopologyAssert extends AbstractObjectAssert<TopologyAssert, Topology> {

  /**
   * @param topology the actual topology to assert against
   */
  public TopologyAssert(final Topology topology) {
    super(topology, TopologyAssert.class);
  }

  /**
   * A convenience factory method that's consistent with AssertJ conventions.
   *
   * @param actual the actual topology to assert against
   * @return an instance of {@link TopologyAssert} to assert properties of the given topology
   */
  public static TopologyAssert assertThat(final Topology actual) {
    return new TopologyAssert(actual);
  }

  /**
   * Asserts that the actual topology is complete. A complete topology is one which has the expected
   * number of brokers, the expected number of partitions, the expected number of replicas per
   * partition, and one leader per partition.
   *
   * <p>This is a convenience method that combines all other assertions from this class.
   *
   * @param clusterSize the expected number of brokers in the cluster
   * @param partitionCount the expected number of partitions in the cluster
   * @param replicationFactor the expected number of replicas per partition
   * @return itself for chaining
   */
  public TopologyAssert isComplete(
      final int clusterSize, final int partitionCount, final int replicationFactor) {
    isNotNull()
        .hasBrokersCount(clusterSize)
        .hasExpectedReplicasCount(partitionCount, replicationFactor)
        .hasLeaderForEachPartition(partitionCount);

    return myself;
  }

  /**
   * Asserts that the topology reports the correct cluster size. Does not check whether the number
   * of brokers is as expected, only the topology metadata.
   *
   * @param clusterSize the expected cluster size
   * @return itself for chaining
   */
  public TopologyAssert hasClusterSize(final int clusterSize) {
    isNotNull();

    if (actual.getClusterSize() != clusterSize) {
      throw failure(
          "Expected cluster size to be <%d> but was <%d>", clusterSize, actual.getClusterSize());
    }

    return myself;
  }

  /**
   * Asserts that the topology reports the right number of partitions. Does not verify that each
   * partition is present via the brokers list, only check the topology metadata.
   *
   * @param partitionsCount the expected partitions count
   * @return itself for chaining
   */
  public TopologyAssert hasPartitionsCount(final int partitionsCount) {
    isNotNull();

    if (actual.getPartitionsCount() != partitionsCount) {
      throw failure(
          "Expected partitions count to be <%d> but was <%d>",
          partitionsCount, actual.getPartitionsCount());
    }

    return myself;
  }

  /**
   * Asserts that the topology reports the expected replication factor. Does not actually check that
   * each reported partition contains the expected number of replicas, but simply the topology's
   * metadata.
   *
   * @param replicationFactor the expected replication factor
   * @return itself for chaining
   */
  public TopologyAssert hasReplicationFactor(final int replicationFactor) {
    isNotNull();

    if (actual.getReplicationFactor() != replicationFactor) {
      throw failure(
          "Expected replication factor to be <%d> but was <%d>",
          replicationFactor, actual.getReplicationFactor());
    }

    return myself;
  }

  /**
   * Asserts that the brokers list contains the expected number of brokers.
   *
   * @param count the expected brokers count
   * @return itself for chaining
   */
  public TopologyAssert hasBrokersCount(final int count) {
    isNotNull().hasClusterSize(count);

    if (actual.getBrokers().size() != count) {
      throw failure(
          "Expected topology to contain <%d> brokers, but it contains <%s>",
          count, actual.getBrokers());
    }

    return myself;
  }

  /**
   * Asserts that each partition has the expected number of replicas.
   *
   * <p>NOTE: this will not work with the fixed partitioning scheme.
   *
   * @param partitionsCount the partition count in the cluster
   * @param replicationFactor the replication factor
   * @return itself for chaining
   */
  public TopologyAssert hasExpectedReplicasCount(
      final int partitionsCount, final int replicationFactor) {
    isNotNull().hasPartitionsCount(partitionsCount).hasReplicationFactor(replicationFactor);

    final Map<Integer, List<PartitionBroker>> partitionMap = buildPartitionsMap();

    if (partitionMap.size() != partitionsCount) {
      throw failure(
          "Expected <%d> partitions to have <%d> replicas, but there are <%d> partitions in the topology: partitions <%s>",
          partitionsCount, replicationFactor, partitionMap.size(), partitionMap.keySet());
    }

    for (final Entry<Integer, List<PartitionBroker>> partitionBrokers : partitionMap.entrySet()) {
      final int partitionId = partitionBrokers.getKey();
      final List<PartitionBroker> brokers = partitionBrokers.getValue();

      if (brokers.size() != replicationFactor) {
        throw failure(
            "Expected partition <%d> to have <%d> replicas, but it has <%d>: brokers <%s>",
            partitionId, replicationFactor, brokers);
      }
    }

    return myself;
  }

  /**
   * Asserts that each partition has exactly one leader.
   *
   * @param partitionsCount the expected number of partitions
   * @return itself for chaining
   */
  public TopologyAssert hasLeaderForEachPartition(final int partitionsCount) {
    isNotNull().hasPartitionsCount(partitionsCount);

    final Map<Integer, List<PartitionBroker>> partitionMap = buildPartitionsMap();

    if (partitionMap.size() != partitionsCount) {
      throw failure(
          "Expected <%d> partitions to have one leader, but there are <%d> partitions in the topology: partitions <%s>",
          partitionsCount, partitionMap.size(), partitionMap.keySet());
    }

    for (final Entry<Integer, List<PartitionBroker>> partitionBrokers : partitionMap.entrySet()) {
      final int partitionId = partitionBrokers.getKey();
      final List<PartitionBroker> brokers = partitionBrokers.getValue();
      final List<PartitionBroker> leaders =
          partitionBrokers.getValue().stream()
              .filter(p -> p.partitionInfo.isLeader())
              .collect(Collectors.toList());

      if (leaders.isEmpty()) {
        throw failure(
            "Expected partition <%d> to have a leader, but it only has the following brokers: <%s>",
            partitionId, brokers);
      }

      if (leaders.size() > 1) {
        throw failure(
            "Expected partition <%d> to have a leader, but it has the following leaders: <%s>",
            partitionId, leaders);
      }
    }

    return myself;
  }

  private Map<Integer, List<PartitionBroker>> buildPartitionsMap() {
    final Map<Integer, List<PartitionBroker>> partitionMap = new HashMap<>();

    for (final BrokerInfo broker : actual.getBrokers()) {
      for (final PartitionInfo partition : broker.getPartitions()) {
        final List<PartitionBroker> partitionBrokers =
            partitionMap.computeIfAbsent(partition.getPartitionId(), ignored -> new ArrayList<>());
        partitionBrokers.add(new PartitionBroker(partition, broker));
      }
    }

    return partitionMap;
  }

  private static final class PartitionBroker {
    private final PartitionInfo partitionInfo;
    private final BrokerInfo brokerInfo;

    private PartitionBroker(final PartitionInfo partitionInfo, final BrokerInfo brokerInfo) {
      this.partitionInfo = partitionInfo;
      this.brokerInfo = brokerInfo;
    }

    @Override
    public String toString() {
      return "PartitionBroker{"
          + "partitionId="
          + partitionInfo.getPartitionId()
          + ", broker="
          + brokerInfo
          + '}';
    }
  }
}
