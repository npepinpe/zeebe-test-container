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
 * Represents a Zeebe standalone gateway. By default, all {@link ZeebePort} ports except {@link
 * ZeebePort#COMMAND} and {@link ZeebePort#MONITORING} are exposed. The monitoring port is not
 * exposed as by default, monitoring is not enabled on a standalone gateway. If you enable it, then
 * you can consider exposing the port - otherwise the ready check will fail.
 *
 * <p>The container is considered ready if:
 *
 * <ul>
 *   <li>its ports are ready (see {@link HostPortWaitStrategy}
 *   <li>the topology check is successful (see {@link #newDefaultTopologyCheck()}
 * </ul>
 *
 * <h2>Connecting to brokers</h2>
 *
 * <p>If you want to connect this gateway to other nodes, the recommended way is to create a new
 * network (e.g. {@link Network#newNetwork()}) and set it as the network of each container you wish
 * to connect together via {@link GenericContainer#setNetwork(Network)}. Furthermore, you have to
 * make sure that all nodes share the same cluster name (see Zeebe documentation on how to configure
 * that).
 *
 * <p>Once done, you can connect this container to a broker by setting this gateway's {@code
 * contactPoint} to the broker's address; if it is a {@link ZeebeNode} you can use {@link
 * ZeebeNode#getInternalClusterAddress()}.
 *
 * <h2>Accessing the gateway</h2>
 *
 * <p>Once started, you can build a new client for it e.g.:
 *
 * <pre>{@code
 * CamundaClient.newClientBuilder()
 *   .grpcAddress(container.getGrpcAddress())
 *   .restAddress(container.getRestAddress())
 *   .usePlaintext()
 *   .build();
 * }</pre>
 *
 * <p>Note that if your client is also a container within the same network, you can and should use
 * the {@link #getInternalGrpcAddress()} and {@link #getInternalRestAddress()} variants.
 */
@API(status = Status.STABLE)
@SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
public class ZeebeGatewayContainer extends GenericContainer<ZeebeGatewayContainer>
    implements ZeebeGatewayNode<ZeebeGatewayContainer> {

  private static final Duration DEFAULT_STARTUP_TIMEOUT = Duration.ofMinutes(1);

  /**
   * Creates new container using the default Zeebe image and version.
   *
   * @see ZeebeDefaults#getDefaultImage()
   * @see ZeebeDefaults#getDefaultVersion()
   */
  public ZeebeGatewayContainer() {
    this(ZeebeDefaults.getInstance().getDefaultDockerImage());
  }

  /**
   * Creates a new container using the given image.
   *
   * @param dockerImageName the base image for the container
   */
  public ZeebeGatewayContainer(final DockerImageName dockerImageName) {
    super(dockerImageName);
    applyDefaultConfiguration();
  }

  /** Returns the default topology check, available for overwriting */
  public static ZeebeTopologyWaitStrategy newDefaultTopologyCheck() {
    return new ZeebeTopologyWaitStrategy().forBrokersCount(1);
  }

  @Override
  public ZeebeGatewayContainer withTopologyCheck(final ZeebeTopologyWaitStrategy topologyCheck) {
    return waitingFor(newDefaultWaitStrategy(topologyCheck));
  }

  @Override
  public ZeebeGatewayContainer withoutTopologyCheck() {
    return waitingFor(new HostPortWaitStrategy().withStartupTimeout(DEFAULT_STARTUP_TIMEOUT));
  }

  private void applyDefaultConfiguration() {
    withNetwork(Network.SHARED)
        .withTopologyCheck(newDefaultTopologyCheck())
        .withEnv("ZEEBE_GATEWAY_NETWORK_HOST", "0.0.0.0")
        .withEnv("ZEEBE_GATEWAY_CLUSTER_MEMBERID", getInternalHost())
        .withEnv("ZEEBE_GATEWAY_CLUSTER_HOST", getInternalHost())
        .withEnv("ZEEBE_STANDALONE_GATEWAY", "true")
        .withStartupTimeout(DEFAULT_STARTUP_TIMEOUT)
        .addExposedPorts(
            ZeebePort.GATEWAY_REST.getPort(),
            ZeebePort.GATEWAY_GRPC.getPort(),
            ZeebePort.INTERNAL.getPort(),
            ZeebePort.MONITORING.getPort());
  }

  private WaitAllStrategy newDefaultWaitStrategy(ZeebeTopologyWaitStrategy topologyCheck) {
    return new WaitAllStrategy(Mode.WITH_OUTER_TIMEOUT)
        .withStrategy(new HostPortWaitStrategy())
        .withStrategy(topologyCheck)
        .withStartupTimeout(DEFAULT_STARTUP_TIMEOUT);
  }
}
