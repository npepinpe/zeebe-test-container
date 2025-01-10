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

import com.github.dockerjava.api.command.InspectContainerResponse;
import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.api.command.TopologyRequestStep1;
import io.camunda.client.api.response.Topology;
import io.camunda.client.impl.CamundaClientFutureImpl;
import io.camunda.client.impl.response.TopologyImpl;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.BrokerInfo;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.Partition;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.Partition.PartitionBrokerRole;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.TopologyResponse;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.rnorth.ducttape.ratelimits.RateLimiter;
import org.rnorth.ducttape.ratelimits.RateLimiterBuilder;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.wait.strategy.WaitStrategyTarget;

/**
 * Note: this suite relies heavily on mocking, as it's quite difficult to test the topology check in
 * a controlled way with real containers/a real client. It doesn't seem ideal, so I'd gladly welcome
 * any suggestions to test it better. Note that it's somewhat integration-tested via the normal
 * container tests.
 */
@ExtendWith(MockitoExtension.class)
@Timeout(value = 5, unit = TimeUnit.MINUTES)
final class ZeebeTopologyWaitStrategyTest {
  private static final BiFunction<Integer, Integer, PartitionBrokerRole> DEFAULT_PARTITIONER =
      (brokerId, partitionId) ->
          brokerId == 0 ? PartitionBrokerRole.LEADER : PartitionBrokerRole.FOLLOWER;
  private static final int BROKERS_COUNT = 3;
  private static final int PARTITIONS_COUNT = 3;
  private static final int REPLICATION_FACTOR = 3;

  @Mock CamundaClient client;
  @Mock CamundaClientBuilder builder;
  @Mock TopologyRequestStep1 topologyRequest;

  @BeforeEach
  void setup() {
    Mockito.when(builder.grpcAddress(Mockito.any())).thenReturn(builder);
    Mockito.when(builder.restAddress(Mockito.any())).thenReturn(builder);
    Mockito.when(builder.build()).thenReturn(client);
    Mockito.when(client.newTopologyRequest()).thenReturn(topologyRequest);
  }

  @Test
  void shouldWaitUntilACompleteTopologyResponse() {
    // given
    final CamundaClientFutureImpl<Topology, TopologyResponse> incompleteTopologyResponse =
        newTopologyResponse(
            newTopology(BROKERS_COUNT - 1, PARTITIONS_COUNT - 1, DEFAULT_PARTITIONER));
    final CamundaClientFutureImpl<Topology, TopologyResponse> completeTopologyResponse =
        newTopologyResponse(newCompleteTopology());
    final WaitStrategyTarget target = new ReachableTarget(1);
    final ZeebeTopologyWaitStrategy strategy = newCompleteWaitStrategy();

    Mockito.when(topologyRequest.send())
        .thenReturn(incompleteTopologyResponse)
        .thenReturn(completeTopologyResponse);
    strategy.forBuilder(() -> builder);

    // when
    strategy.waitUntilReady(target);

    // then
    Mockito.verify(topologyRequest, Mockito.timeout(5000).times(2)).send();
  }

  @Test
  void shouldUseCustomGatewayPort() {
    final CamundaClientFutureImpl<Topology, TopologyResponse> topologyResponse =
        newTopologyResponse(newCompleteTopology());
    final ReachableTarget target = new ReachableTarget(1);
    final ZeebeTopologyWaitStrategy strategy = newCompleteWaitStrategy().forGatewayPort(2);

    Mockito.when(topologyRequest.send()).thenReturn(topologyResponse);
    strategy.forBuilder(() -> builder);

    // when
    strategy.waitUntilReady(target);

    // then
    Assertions.assertThat(target.originalPort)
        .as("the original port (i.e. unmapped) should be the one given at configuration")
        .isEqualTo(2);
  }

  @Test
  void shouldUseCorrectGatewayAddress() {
    final CamundaClientFutureImpl<Topology, TopologyResponse> topologyResponse =
        newTopologyResponse(newCompleteTopology());
    final ReachableTarget target = new ReachableTarget(1);
    final ZeebeTopologyWaitStrategy strategy = newCompleteWaitStrategy();

    Mockito.when(client.newTopologyRequest()).thenReturn(topologyRequest);
    Mockito.when(topologyRequest.send()).thenReturn(topologyResponse);
    strategy.forBuilder(() -> builder);

    // when
    strategy.waitUntilReady(target);

    // then
    final URI expectedAddress = URI.create("http://" + target.getHost() + ":" + target.mappedPort);
    Mockito.verify(builder, Mockito.timeout(5000).atLeastOnce()).grpcAddress(expectedAddress);
    Mockito.verify(builder, Mockito.timeout(5000).atLeastOnce()).restAddress(expectedAddress);
  }

  @ParameterizedTest(name = "should timeout on incomplete topology when {0}")
  @MethodSource("strategyProvider")
  void shouldTimeoutOnIncompleteTopologies(
      final String testName,
      final TopologyResponse topology,
      final ZeebeTopologyWaitStrategy strategy) {
    final CamundaClientFutureImpl<Topology, TopologyResponse> topologyResponse =
        newTopologyResponse(topology);
    final ReachableTarget target = new ReachableTarget(1);

    Mockito.when(client.newTopologyRequest()).thenReturn(topologyRequest);
    Mockito.when(topologyRequest.send()).thenReturn(topologyResponse);
    strategy.forBuilder(() -> builder);

    // when - then
    Assertions.assertThatThrownBy(() -> strategy.waitUntilReady(target))
        .as("the strategy times out due to being incomplete because: " + testName)
        .isInstanceOf(ContainerLaunchException.class);
    Mockito.verify(topologyRequest, Mockito.atLeastOnce()).send();
  }

  private CamundaClientFutureImpl<Topology, TopologyResponse> newTopologyResponse(
      final TopologyResponse topology) {
    final CamundaClientFutureImpl<Topology, TopologyResponse> response =
        new CamundaClientFutureImpl<>(TopologyImpl::new);
    response.onNext(topology);

    return response;
  }

  private static TopologyResponse newTopology(
      final int brokersCount,
      final int partitionsCount,
      final BiFunction<Integer, Integer, PartitionBrokerRole> partitioner) {
    final TopologyResponse.Builder topologyBuilder = TopologyResponse.newBuilder();
    for (int brokerId = 0; brokerId < brokersCount; brokerId++) {
      final BrokerInfo.Builder brokerBuilder = BrokerInfo.newBuilder().setNodeId(brokerId);
      for (int partitionId = 0; partitionId < partitionsCount; partitionId++) {
        final PartitionBrokerRole role = partitioner.apply(brokerId, partitionId);
        if (role != null) {
          brokerBuilder.addPartitions(
              Partition.newBuilder().setPartitionId(partitionId).setRole(role));
        }
      }

      topologyBuilder.addBrokers(brokerBuilder);
    }

    return topologyBuilder.build();
  }

  /**
   * Returns a wait strategy which is configured for the topology returned by {@link
   * #newCompleteTopology()}.
   *
   * <p>The strategy is also configured to speed tests up by having a rate limiter which allows for
   * 1 request every 10ms, and a timeout of 100ms.
   */
  private static ZeebeTopologyWaitStrategy newCompleteWaitStrategy() {
    final ZeebeTopologyWaitStrategy strategy =
        new ZeebeTopologyWaitStrategy()
            .forBrokersCount(BROKERS_COUNT)
            .forPartitionsCount(PARTITIONS_COUNT)
            .forReplicationFactor(REPLICATION_FACTOR);
    final RateLimiter rateLimiter =
        RateLimiterBuilder.newBuilder()
            .withConstantThroughput()
            .withRate(100, TimeUnit.SECONDS)
            .build();
    strategy.withRateLimiter(rateLimiter).withStartupTimeout(Duration.ofMillis(100));

    return strategy;
  }

  private static TopologyResponse newCompleteTopology() {
    return newTopology(BROKERS_COUNT, PARTITIONS_COUNT, DEFAULT_PARTITIONER);
  }

  private static Stream<Arguments> strategyProvider() {

    return Stream.of(
        Arguments.arguments(
            "missing a broker",
            newCompleteTopology(),
            newCompleteWaitStrategy().forBrokersCount(BROKERS_COUNT + 1)),
        Arguments.arguments(
            "missing a partition",
            newCompleteTopology(),
            newCompleteWaitStrategy().forPartitionsCount(PARTITIONS_COUNT + 1)),
        Arguments.arguments(
            "missing a replica",
            newCompleteTopology(),
            newCompleteWaitStrategy().forReplicationFactor(REPLICATION_FACTOR + 1)),
        Arguments.arguments(
            "missing a leader",
            newTopology(
                3,
                3,
                (brokerId, partitionId) ->
                    partitionId == 2
                        ? PartitionBrokerRole.FOLLOWER
                        : DEFAULT_PARTITIONER.apply(brokerId, partitionId)),
            newCompleteWaitStrategy()));
  }

  private static final class ReachableTarget implements WaitStrategyTarget {
    private final InspectContainerResponse containerInfo;
    private final int mappedPort;
    private int originalPort;

    @SuppressWarnings("SameParameterValue")
    private ReachableTarget(final int mappedPort) {
      this.mappedPort = mappedPort;
      this.containerInfo = new InspectContainerResponse();
    }

    @Override
    public String getHost() {
      return "127.0.0.1";
    }

    @Override
    public Integer getMappedPort(final int originalPort) {
      this.originalPort = originalPort;
      return mappedPort;
    }

    @Override
    public List<Integer> getExposedPorts() {
      return Collections.emptyList();
    }

    @Override
    public InspectContainerResponse getContainerInfo() {
      return containerInfo;
    }
  }
}
