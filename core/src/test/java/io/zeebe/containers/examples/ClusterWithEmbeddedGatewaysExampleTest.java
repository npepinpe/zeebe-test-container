/*
 * Copyright © 2019 camunda services GmbH (info@camunda.com)
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
package io.zeebe.containers.examples;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.BrokerInfo;
import io.camunda.client.api.response.Topology;
import io.zeebe.containers.ZeebeBrokerNode;
import io.zeebe.containers.ZeebeContainer;
import io.zeebe.containers.ZeebeTopologyWaitStrategy;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.lifecycle.Startable;
import org.testcontainers.lifecycle.Startables;

/**
 * This showcases how to start a cluster of brokers with embedded gateways, removing the need for a
 * standalone gateway container.
 *
 * <p>One thing to note is that we cannot make use of the {@link Testcontainers} extension here, as
 * it will start containers sequentially and not in parallel. A Zeebe broker, the first time it is
 * started, will wait for all nodes in the cluster to be present before it can start. This is due to
 * the partitioning scheme and is only necessary on the very first run. Nevertheless, this prevents
 * us from the using the extension, and the container's lifecycle must be managed separately.
 */
final class ClusterWithEmbeddedGatewaysExampleTest {
  private final Network network = Network.newNetwork();
  private final List<ZeebeContainer> containers =
      Arrays.asList(new ZeebeContainer(), new ZeebeContainer(), new ZeebeContainer());
  private final ZeebeContainer nodeZeroContainer = getConfiguredClusterBroker(0, containers);
  private final ZeebeContainer nodeOneContainer = getConfiguredClusterBroker(1, containers);
  private final ZeebeContainer nodeTwoContainer = getConfiguredClusterBroker(2, containers);

  @AfterEach
  void tearDown() {
    containers.parallelStream().forEach(Startable::stop);
    network.close();
  }

  @Test
  @Timeout(value = 15, unit = TimeUnit.MINUTES)
  void shouldStartCluster() {
    // given
    Startables.deepStart(containers).join();

    // when
    for (final ZeebeContainer gateway : containers) {
      try (final CamundaClient client = newZeebeClient(gateway)) {
        // then
        final Topology topology = client.newTopologyRequest().send().join(5, TimeUnit.SECONDS);
        final List<BrokerInfo> brokers = topology.getBrokers();
        Assertions.assertThat(brokers)
            .as("the topology contains all the brokers, advertising the correct address")
            .hasSize(3)
            .extracting(BrokerInfo::getAddress)
            .containsExactlyInAnyOrder(
                nodeZeroContainer.getInternalCommandAddress(),
                nodeOneContainer.getInternalCommandAddress(),
                nodeTwoContainer.getInternalCommandAddress());
      }
    }
  }

  /**
   * Will configure the broker in {@code brokers} at index {@code index} for a basic cluster that
   * contains all {@code brokers}. Note that depending on the machine on which you're running, the
   * containers may be quite slow, so make sure to provide them enough cores/memory.
   *
   * @param index the index of the broker to configure and return in {@code brokers}
   * @param brokers all the brokers part of the cluster
   * @return the broker at index {@code index} in {@code brokers}, configured for clustering
   */
  private ZeebeContainer getConfiguredClusterBroker(
      final int index, final List<ZeebeContainer> brokers) {
    final int clusterSize = brokers.size();
    final String initialContactPoints =
        brokers.stream()
            .map(ZeebeBrokerNode::getInternalClusterAddress)
            .collect(Collectors.joining(","));
    final ZeebeContainer broker = brokers.get(index);

    return broker
        .withTopologyCheck(
            new ZeebeTopologyWaitStrategy()
                .forBrokersCount(clusterSize)
                .forReplicationFactor(clusterSize))
        .withStartupTimeout(Duration.ofMinutes(5))
        .withNetwork(network)
        .withEnv("ZEEBE_BROKER_CLUSTER_NODEID", String.valueOf(index))
        .withEnv("ZEEBE_BROKER_CLUSTER_CLUSTERSIZE", String.valueOf(clusterSize))
        .withEnv("ZEEBE_BROKER_CLUSTER_REPLICATIONFACTOR", String.valueOf(clusterSize))
        .withEnv("ZEEBE_BROKER_CLUSTER_INITIALCONTACTPOINTS", initialContactPoints);
  }

  private CamundaClient newZeebeClient(final ZeebeContainer node) {
    return CamundaClient.newClientBuilder()
        .grpcAddress(node.getGrpcAddress())
        .restAddress(node.getRestAddress())
        .usePlaintext()
        .build();
  }
}
