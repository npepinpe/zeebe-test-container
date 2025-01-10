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
package io.zeebe.containers.examples.cluster;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.BrokerInfo;
import io.camunda.client.api.response.Topology;
import io.zeebe.containers.cluster.ZeebeCluster;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * This showcases how to start a cluster of brokers with embedded gateways, removing the need for a
 * standalone gateway container.
 */
@Testcontainers
final class ZeebeClusterWithEmbeddedGatewaysExampleTest {
  private final Network network = Network.newNetwork();

  @Container
  private final ZeebeCluster cluster =
      ZeebeCluster.builder()
          .withGatewaysCount(0)
          .withBrokersCount(2)
          .withPartitionsCount(2)
          .withReplicationFactor(1)
          .withEmbeddedGateway(true)
          .withNetwork(network)
          .build();

  @AfterEach
  void afterEach() {
    network.close();
  }

  @Test
  @Timeout(value = 15, unit = TimeUnit.MINUTES)
  void shouldStartCluster() {
    // given
    final Topology topology;

    // when
    try (final CamundaClient client = cluster.newClientBuilder().build()) {
      topology = client.newTopologyRequest().send().join(5, TimeUnit.SECONDS);
    }

    // then
    final List<BrokerInfo> brokers = topology.getBrokers();
    Assertions.assertThat(brokers)
        .as("the topology contains all the brokers, advertising the expected address")
        .hasSize(2)
        .extracting(BrokerInfo::getAddress)
        .containsExactlyInAnyOrder(
            cluster.getBrokers().get(0).getInternalCommandAddress(),
            cluster.getBrokers().get(1).getInternalCommandAddress());
  }
}
