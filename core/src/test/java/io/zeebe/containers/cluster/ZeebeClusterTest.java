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
package io.zeebe.containers.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.Topology;
import io.zeebe.containers.ZeebeGatewayNode;
import io.zeebe.containers.util.TopologyAssert;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Network;

final class ZeebeClusterTest {
  @AutoClose private final Network network = Network.newNetwork();

  @AutoClose private ZeebeCluster cluster;

  @Test
  void shouldStartSingleNodeCluster() {
    // given
    cluster =
        ZeebeCluster.builder()
            .withEmbeddedGateway(true)
            .withReplicationFactor(1)
            .withPartitionsCount(1)
            .withBrokersCount(1)
            .withNetwork(network)
            .build();

    // when
    cluster.start();

    // then
    final Topology topology;
    try (final CamundaClient client = cluster.newClientBuilder().build()) {
      topology = client.newTopologyRequest().send().join();
    }

    assertThat(topology.getPartitionsCount())
        .as("there is exactly one partition as configured")
        .isOne();
    assertThat(topology.getReplicationFactor())
        .as("there is a replication factor of 1 as configured")
        .isOne();
    TopologyAssert.assertThat(topology)
        .as("the topology is complete for a one broker, one partition cluster")
        .hasBrokersCount(1)
        .isComplete(1, 1, 1);
  }

  @Test
  void shouldStartClusterWithEmbeddedGateways() {
    // given
    cluster =
        ZeebeCluster.builder()
            .withEmbeddedGateway(true)
            .withReplicationFactor(2)
            .withPartitionsCount(2)
            .withBrokersCount(2)
            .withNetwork(network)
            .build();

    // when
    cluster.start();

    // then
    for (final ZeebeGatewayNode<?> gateway : cluster.getGateways().values()) {
      final Topology topology;
      try (final CamundaClient client =
          cluster
              .newClientBuilder()
              .grpcAddress(gateway.getGrpcAddress())
              .restAddress(gateway.getRestAddress())
              .build()) {
        topology = client.newTopologyRequest().send().join();
      }

      assertThat(topology.getReplicationFactor())
          .as("there is replication factor of 2 as configured")
          .isEqualTo(2);
      assertThat(topology.getPartitionsCount())
          .as("there are exactly two partitions as configured")
          .isEqualTo(2);
      TopologyAssert.assertThat(topology)
          .as("the topology is complete with 2 partitions and 2 brokers")
          .hasBrokersCount(2)
          .isComplete(2, 2, 2);
    }
  }

  @Test
  void shouldStartClusterWithStandaloneGateway() {
    // given
    cluster =
        ZeebeCluster.builder()
            .withEmbeddedGateway(false)
            .withReplicationFactor(1)
            .withPartitionsCount(1)
            .withBrokersCount(1)
            .withGatewaysCount(1)
            .withNetwork(network)
            .build();

    // when
    cluster.start();

    // then
    final Topology topology;
    try (final CamundaClient client = cluster.newClientBuilder().build()) {
      topology = client.newTopologyRequest().send().join();
    }

    assertThat(topology.getPartitionsCount())
        .as("there is exactly one partition as configured")
        .isOne();
    assertThat(topology.getReplicationFactor())
        .as("there is a replication factor of 1 as configured")
        .isOne();
    TopologyAssert.assertThat(topology)
        .as("the topology is complete for a one broker, one partition cluster")
        .hasBrokersCount(1)
        .isComplete(1, 1, 1);
  }

  @Test
  void shouldStartClusterWithMixedGateways() {
    // given
    cluster =
        ZeebeCluster.builder()
            .withEmbeddedGateway(true)
            .withReplicationFactor(1)
            .withPartitionsCount(1)
            .withBrokersCount(1)
            .withGatewaysCount(1)
            .withNetwork(network)
            .build();

    // when
    cluster.start();

    // then
    for (final ZeebeGatewayNode<?> gateway : cluster.getGateways().values()) {
      try (final CamundaClient client =
          CamundaClient.newClientBuilder()
              .usePlaintext()
              .grpcAddress(gateway.getGrpcAddress())
              .restAddress(gateway.getRestAddress())
              .build()) {
        final Topology topology = client.newTopologyRequest().send().join();
        assertThat(topology.getPartitionsCount())
            .as("there is exactly one partition as configured")
            .isOne();
        assertThat(topology.getReplicationFactor())
            .as("there is a replication factor of 1 as configured")
            .isOne();
        TopologyAssert.assertThat(topology)
            .as("the topology is complete for a one broker, one partition cluster")
            .isComplete(1, 1, 1)
            .hasBrokersCount(1);
      }
    }
  }
}
