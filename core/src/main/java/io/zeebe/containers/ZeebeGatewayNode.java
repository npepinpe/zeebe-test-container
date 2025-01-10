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

import java.net.URI;
import org.apiguardian.api.API;
import org.apiguardian.api.API.Status;
import org.testcontainers.containers.GenericContainer;

/**
 * Represents common properties of nodes which can act as a gateway for a Zeebe cluster, e.g. {@link
 * ZeebeContainer} or {@link ZeebeGatewayContainer}.
 *
 * <p>You should typically use {@link #getGrpcAddress()} and {@link #getRestAddress()} to wire your
 * clients with this gateway. If your client happens to be running in the same network as the Docker
 * container, then you may want to use {@link #getInternalGrpcAddress()} or {@link
 * #getInternalRestAddress()} instead.
 *
 * @param <T> the concrete type of the underlying container
 */
@API(status = Status.STABLE)
@SuppressWarnings({"UnusedReturnValue", "unused"})
public interface ZeebeGatewayNode<T extends GenericContainer<T> & ZeebeGatewayNode<T>>
    extends ZeebeNode<T> {

  /**
   * Override the default topology check to change the number of expected partitions or the gateway
   * port if necessary. Overwrites previously set wait strategies with the container's default wait
   * strategy and the given topology check.
   *
   * <p>For example, to change the number of expected partitions for a complete topology, you can
   * do:
   *
   * <pre>{@code
   * gateway.withTopologyCheck(new TopologyWaitStrategy().forExpectedPartitionsCount(3));
   * }</pre>
   *
   * <p>NOTE: this may mutate the underlying wait strategy, so if you are configured a specific
   * startup timeout, it will need to be applied again after a call to this method.
   *
   * @param topologyCheck the new topology check
   * @return this container, for chaining
   */
  T withTopologyCheck(final ZeebeTopologyWaitStrategy topologyCheck);

  /**
   * Convenience method to disable the topology check. Overwrites previously set wait strategies
   * with the container's default wait strategy, without the topology check.
   *
   * @return this container, for chaining
   */
  T withoutTopologyCheck();

  /**
   * Returns address a client which is not part of the container's network should use. If you're
   * unsure whether you need to use the external or internal address, then you most likely want the
   * external address.
   *
   * <p>You can build your client like this:
   *
   * <pre>@{code
   *   CamundaClient.newClientBuilder()
   *     .gatewayAddress(container.getExternalGatewayAddress())
   *     .usePlaintext()
   *     .build();
   * }</pre>
   *
   * @return the gateway address visible from outside the docker network
   * @deprecated Use the protocol specific variants from now on, {@link #getGrpcAddress()} or {@link
   *     #getRestAddress()}
   */
  @Deprecated
  default String getExternalGatewayAddress() {
    return getExternalAddress(ZeebePort.GATEWAY.getPort());
  }

  /**
   * Returns an address a client which is part of the container's network can use.
   *
   * <p>You can build your client like this:
   *
   * <pre>@{code
   *   CamundaClient.newClientBuilder()
   *     .gatewayAddress(container.getInternalGatewayAddress())
   *     .usePlaintext()
   *     .build();
   * }</pre>
   *
   * @return the gateway address visible from within the docker network
   * @deprecated Use the protocol specific variants from now on, {@link #getInternalGrpcAddress()}
   *     or {@link #getInternalRestAddress()}
   */
  @Deprecated
  default String getInternalGatewayAddress() {
    return getInternalAddress(ZeebePort.GATEWAY.getPort());
  }

  /**
   * Returns an address accessible from within the container's network for the REST API. Primarily
   * meant to be used by clients.
   *
   * <p>You can build your client like this:
   *
   * <pre>@{code
   *   CamundaClient.newClientBuilder()
   *     .restAddress(container.getInternalRestUrl())
   *     .usePlaintext()
   *     .build();
   * }</pre>
   *
   * @return internally accessible REST API address
   */
  default URI getInternalRestAddress() {
    return getInternalRestAddress("http");
  }

  /**
   * Returns an address accessible from within the container's network for the REST API. Primarily
   * meant to be used by clients.
   *
   * <p>Use this variant if you need to specify a different scheme, e.g. HTTPS.
   *
   * <p>You can build your client like this:
   *
   * <pre>@{code
   *   CamundaClient.newClientBuilder()
   *     .restAddress(container.getInternalRestUrl("https"))
   *     .build();
   * }</pre>
   *
   * @param scheme the expected scheme (e.g. HTTP, HTTPS)
   * @return internally accessible REST API address
   */
  default URI getInternalRestAddress(final String scheme) {
    final int port = ZeebePort.GATEWAY_REST.getPort();
    return URI.create(String.format("%s://%s:%d", scheme, getInternalHost(), port));
  }

  /**
   * Returns the address of the REST API a client which is not part of the container's network
   * should use. If you want an address accessible from within the container's own network, use *
   * {@link #getInternalRestAddress()}
   *
   * <p>You can build your client like this:
   *
   * <pre>@{code
   *   CamundaClient.newClientBuilder()
   *     .restAddress(container.getRestAddress())
   *     .usePlaintext()
   *     .build();
   * }</pre>
   *
   * @return externally accessible REST API address
   */
  default URI getRestAddress() {
    return getRestAddress("http");
  }

  /**
   * Returns the address of the REST API a client which is not part of the container's network
   * should use. If you want an address accessible from within the container's own network, use
   * {@link #getInternalRestAddress(String)}.
   *
   * <p>Use this method if you need to specify a different connection scheme, e.g. HTTPS.
   *
   * <p>You can build your client like this:
   *
   * <pre>@{code
   *   CamundaClient.newClientBuilder()
   *     .restAddress(container.getExternalRestAddress("https"))
   *     .build();
   * }</pre>
   *
   * @param scheme the expected scheme (e.g. HTTP, HTTPS)
   * @return externally accessible REST API address
   */
  default URI getRestAddress(final String scheme) {
    final int port = getMappedPort(ZeebePort.GATEWAY_REST.getPort());
    return URI.create(String.format("%s://%s:%d", scheme, getExternalHost(), port));
  }

  /**
   * Returns an address accessible from within the container's network for the gRPC API. Primarily
   * meant to be used by clients.
   *
   * <p>You can build your client like this:
   *
   * <pre>@{code
   *   CamundaClient.newClientBuilder()
   *     .grpcAddress(container.getInternalGrpcAddress())
   *     .usePlaintext()
   *     .build();
   * }</pre>
   *
   * @return internally accessible REST API address
   */
  default URI getInternalGrpcAddress() {
    return getInternalGrpcAddress("http");
  }

  /**
   * Returns an address accessible from within the container's network for the REST API. Primarily
   * meant to be used by clients.
   *
   * <p>Use this variant if you need to specify a different scheme, e.g. HTTPS.
   *
   * <p>You can build your client like this:
   *
   * <pre>@{code
   *   CamundaClient.newClientBuilder()
   *     .grpcAddress(container.getInternalGrpcAddress("https"))
   *     .build();
   * }</pre>
   *
   * @param scheme the expected scheme (e.g. HTTP, HTTPS)
   * @return internally accessible REST API address
   */
  default URI getInternalGrpcAddress(final String scheme) {
    final int port = ZeebePort.GATEWAY_REST.getPort();
    return URI.create(String.format("%s://%s:%d", scheme, getInternalHost(), port));
  }

  /**
   * Returns the address of the gRPC API a client which is not part of the container's network
   * should use. If you want an address accessible from within the container's own network, use
   * {@link #getInternalGrpcAddress()}.
   *
   * <p>You can build your client like this:
   *
   * <pre>@{code
   *   CamundaClient.newClientBuilder()
   *     .grpcAddress(container.getGrpcAddress())
   *     .usePlaintext()
   *     .build();
   * }</pre>
   *
   * @return externally accessible gRPC API address
   */
  default URI getGrpcAddress() {
    return getGrpcAddress("http");
  }

  /**
   * Returns the address of the gRPC API a client which is not part of the container's network
   * should use. If you want an address accessible from within the container's own network, use
   * {@link #getInternalGrpcAddress(String)}.
   *
   * <p>Use this method if you need to specify a different connection scheme, e.g. HTTPS.
   *
   * <p>You can build your client like this:
   *
   * <pre>@{code
   *   CamundaClient.newClientBuilder()
   *     .grpcAddress(container.getGrpcAddress("https"))
   *     .build();
   * }</pre>
   *
   * @param scheme the expected scheme (e.g. HTTP, HTTPS)
   * @return externally accessible gRPC API address
   */
  default URI getGrpcAddress(final String scheme) {
    final int port = getMappedPort(ZeebePort.GATEWAY_GRPC.getPort());
    return URI.create(String.format("%s://%s:%d", scheme, getExternalHost(), port));
  }
}
