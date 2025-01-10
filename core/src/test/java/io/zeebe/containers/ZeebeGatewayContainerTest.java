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
package io.zeebe.containers;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.BrokerInfo;
import io.camunda.client.api.response.Topology;
import io.zeebe.containers.util.TestSupport;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
final class ZeebeGatewayContainerTest {
  private final Network network = Network.newNetwork();

  @Container
  private final ZeebeBrokerContainer brokerContainer =
      new ZeebeBrokerContainer().withNetwork(network);

  @Container
  private final ZeebeGatewayContainer gatewayContainer =
      new ZeebeGatewayContainer()
          .withNetwork(network)
          .withEnv(
              "ZEEBE_GATEWAY_CLUSTER_CONTACTPOINT", brokerContainer.getInternalClusterAddress());

  @AfterEach
  void afterEach() {
    network.close();
  }

  @Test
  void shouldConnectToBroker() {
    // given
    final Topology topology;

    // when
    try (final CamundaClient client = TestSupport.newZeebeClient(gatewayContainer)) {
      topology = client.newTopologyRequest().send().join(5, TimeUnit.SECONDS);
    }

    // then
    final List<BrokerInfo> brokers = topology.getBrokers();
    Assertions.assertThat(brokers).as("the gateway should report one broker").hasSize(1);
    Assertions.assertThat(brokers.get(0).getAddress())
        .as("the gateway should report the correct contact point")
        .isEqualTo(brokerContainer.getInternalCommandAddress());
  }
}
