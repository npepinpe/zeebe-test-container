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
package io.zeebe.containers.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.within;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.zeebe.protocol.record.Record;
import io.zeebe.containers.ZeebeGatewayNode;
import io.zeebe.containers.clock.ZeebeClock;
import io.zeebe.containers.cluster.ZeebeCluster;
import io.zeebe.containers.exporter.DebugReceiver;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
final class ZeebeClusterEngineIT {
  @AutoClose private final Network network = Network.newNetwork();
  private final InfiniteList<Record<?>> records = new InfiniteList<>();

  @AutoClose private final DebugReceiver receiver = new DebugReceiver(records::add);

  @AutoClose
  private final DebugReceiverStream recordStream = new DebugReceiverStream(records, receiver);

  @AutoClose
  private final ZeebeCluster cluster =
      ZeebeCluster.builder()
          .withEmbeddedGateway(true)
          .withBrokersCount(2)
          .withPartitionsCount(1)
          .withReplicationFactor(2)
          .withNetwork(network)
          .build();

  @Test
  void shouldCloseEverythingOnStop() {
    // given
    final CamundaClient client;
    final InetSocketAddress receiverAddress;
    try (final ZeebeClusterEngine engine = new ZeebeClusterEngine(cluster, recordStream)) {
      engine.start();
      client = engine.createClient();
      receiverAddress = receiver.serverAddress();
    }

    // then
    Awaitility.await("until the client is fully shutdown")
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              final FinalCommandStep<?> request = client.newTopologyRequest();
              assertThatCode(request::send)
                  .hasRootCauseInstanceOf(RejectedExecutionException.class);
            });
    assertThat(cluster.getNodes().values()).allMatch(node -> !node.isRunning());
    assertThatCode(() -> testServerConnection(receiverAddress))
        .isInstanceOf(ConnectException.class);
  }

  private void testServerConnection(final InetSocketAddress receiverAddress) throws IOException {
    try (final Socket socket = new Socket()) {
      socket.connect(receiverAddress, 1_000);
    }
  }

  @Nested
  final class WithClusterTest {
    @Container final ZeebeClusterEngine engine = new ZeebeClusterEngine(cluster, recordStream);

    @Test
    void shouldCreateClient() {
      // given

      // when
      final CamundaClient client = engine.createClient();

      // then
      assertThat((Future<?>) client.newTopologyRequest().send())
          .succeedsWithin(Duration.ofSeconds(1));
    }

    @SuppressWarnings("deprecation")
    @Test
    void shouldReturnGatewayAddress() {
      // given

      // when
      final String address = engine.getGatewayAddress();

      // then
      final Set<String> gatewayAddress =
          cluster.getGateways().values().stream()
              .map(ZeebeGatewayNode::getExternalGatewayAddress)
              .collect(Collectors.toSet());
      assertThat(address).isIn(gatewayAddress);
    }

    @Test
    void shouldIncreaseTime() {
      // given
      final Duration offset = Duration.ofMinutes(5);
      final Map<String, ZeebeClock> clocks = new HashMap<>();
      cluster.getNodes().forEach((id, node) -> clocks.put(id, ZeebeClock.newDefaultClock(node)));
      final Map<String, Instant> startTimes = new HashMap<>();
      clocks.forEach((id, clock) -> startTimes.put(id, clock.getCurrentTime()));

      // when
      engine.increaseTime(offset);

      // then
      final Map<String, Instant> endTimes = new HashMap<>();
      clocks.forEach((id, clock) -> endTimes.put(id, clock.getCurrentTime()));

      assertThat(endTimes)
          .allSatisfy(
              (id, endTime) ->
                  assertThat(endTime)
                      .isCloseTo(startTimes.get(id).plus(offset), within(10, ChronoUnit.SECONDS)));
    }
  }
}
