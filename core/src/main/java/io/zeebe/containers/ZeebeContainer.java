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

import java.time.Duration;
import org.apiguardian.api.API;
import org.apiguardian.api.API.Status;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy.Mode;
import org.testcontainers.utility.DockerImageName;

/**
 * Represents a standalone Zeebe broker, that is, a broker with an embedded gateway. By default, all
 * {@link ZeebePort} ports are exposed, and the container is considered ready if:
 *
 * <ul>
 *   <li>its ports are ready (see {@link HostPortWaitStrategy}
 *   <li>the broker check is successful (see {@link
 *       ZeebeBrokerContainer#newDefaultBrokerReadyCheck()}
 *   <li>the topology check is successful (see {@link
 *       ZeebeGatewayContainer#newDefaultTopologyCheck()}
 * </ul>
 *
 * <p>Once started, you can build a new client for it e.g.:
 *
 * <pre>{@code
 * CamundaClient.newClientBuilder()
 *   .brokerContainerPoint(container.getExternalGatewayAddress())
 *   .usePlaintext()
 *   .build();
 * }</pre>
 *
 * <p>If you want to reuse the same data across restarts, you can specify it using {@link
 * ZeebeBrokerNode#withZeebeData(ZeebeData)}.
 */
@API(status = Status.STABLE)
@SuppressWarnings("java:S2160")
public class ZeebeContainer extends GenericContainer<ZeebeContainer>
    implements ZeebeGatewayNode<ZeebeContainer>, ZeebeBrokerNode<ZeebeContainer> {

  private static final Duration DEFAULT_STARTUP_TIMEOUT = Duration.ofMinutes(1);

  /**
   * Creates a new container with the default Zeebe image and version.
   *
   * @see ZeebeDefaults#getDefaultImage()
   * @see ZeebeDefaults#getDefaultVersion()
   */
  public ZeebeContainer() {
    this(ZeebeDefaults.getInstance().getDefaultDockerImage());
  }

  /**
   * @param dockerImageName the full docker image name to use
   */
  public ZeebeContainer(final DockerImageName dockerImageName) {
    super(dockerImageName);
    applyDefaultConfiguration();
  }

  @Override
  public ZeebeContainer withTopologyCheck(final ZeebeTopologyWaitStrategy topologyCheck) {
    return waitingFor(newDefaultWaitStrategy().withStrategy(topologyCheck));
  }

  @Override
  public ZeebeContainer withoutTopologyCheck() {
    return waitingFor(newDefaultWaitStrategy());
  }

  /** Returns the default wait strategy for this container */
  protected WaitAllStrategy newDefaultWaitStrategy() {
    return new WaitAllStrategy(Mode.WITH_OUTER_TIMEOUT)
        .withStrategy(new HostPortWaitStrategy())
        .withStrategy(ZeebeBrokerContainer.newDefaultBrokerReadyCheck())
        .withStartupTimeout(DEFAULT_STARTUP_TIMEOUT);
  }

  private void applyDefaultConfiguration() {
    withNetwork(Network.SHARED)
        .withTopologyCheck(ZeebeGatewayContainer.newDefaultTopologyCheck())
        .withEnv("ZEEBE_BROKER_GATEWAY_ENABLE", "true")
        .withEnv("ZEEBE_BROKER_NETWORK_HOST", "0.0.0.0")
        .withEnv("ZEEBE_BROKER_NETWORK_ADVERTISEDHOST", getInternalHost())
        .addExposedPorts(
            ZeebePort.GATEWAY_REST.getPort(),
            ZeebePort.GATEWAY_GRPC.getPort(),
            ZeebePort.COMMAND.getPort(),
            ZeebePort.INTERNAL.getPort(),
            ZeebePort.MONITORING.getPort());
  }
}
