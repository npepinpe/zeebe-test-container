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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.zeebe.process.test.api.RecordStreamSource;
import io.zeebe.containers.ZeebeBrokerNode;
import io.zeebe.containers.ZeebeContainer;
import io.zeebe.containers.ZeebeGatewayNode;
import io.zeebe.containers.clock.ZeebeClock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.UnaryOperator;
import org.agrona.CloseHelper;
import org.apiguardian.api.API;
import org.apiguardian.api.API.Status;
import org.testcontainers.containers.GenericContainer;

/**
 * A {@link ContainerEngine} which wraps a single {@link ZeebeContainer}, which is both a gateway
 * and a broker.
 */
@API(status = Status.INTERNAL)
final class ZeebeContainerEngine<
        T extends GenericContainer<T> & ZeebeGatewayNode<T> & ZeebeBrokerNode<T>>
    implements TestAwareContainerEngine {
  private final List<CamundaClient> clients = new ArrayList<>();
  private final DebugReceiverStream recordStream;
  private final T container;
  private final ZeebeClock clock;

  ZeebeContainerEngine(final T container, final DebugReceiverStream recordStream) {
    this.container = container.withEnv("ZEEBE_CLOCK_CONTROLLED", "true");
    this.recordStream = recordStream;

    clock = ZeebeClock.newDefaultClock(container);
  }

  @Override
  public void acknowledge(final int partitionId, final long position) {
    recordStream.acknowledge(partitionId, position);
  }

  @Override
  public RecordStreamSource getRecordStreamSource() {
    return recordStream;
  }

  @Override
  public CamundaClient createClient() {
    return createClient(UnaryOperator.identity());
  }

  @Override
  public CamundaClient createClient(final ObjectMapper objectMapper) {
    return createClient(b -> b.withJsonMapper(new CamundaObjectMapper(objectMapper)));
  }

  @SuppressWarnings("deprecation")
  @Override
  public String getGatewayAddress() {
    return container.getExternalGatewayAddress();
  }

  @Override
  public void increaseTime(final Duration timeToAdd) {
    clock.addTime(timeToAdd);
  }

  @Override
  public void waitForIdleState(final Duration timeout)
      throws InterruptedException, TimeoutException {
    recordStream.waitForIdleState(timeout);
  }

  @Override
  public void waitForBusyState(final Duration timeout)
      throws InterruptedException, TimeoutException {
    recordStream.waitForBusyState(timeout);
  }

  @Override
  public void start() {
    recordStream.start(Collections.singleton(container));
    container.start();
  }

  @Override
  public void stop() {
    CloseHelper.closeAll(clients);
    clients.clear();

    CloseHelper.closeAll(container, recordStream);
  }

  private CamundaClient createClient(final UnaryOperator<CamundaClientBuilder> configurator) {
    final CamundaClientBuilder builder =
        configurator.apply(
            CamundaClient.newClientBuilder()
                .usePlaintext()
                .grpcAddress(container.getGrpcAddress())
                .restAddress(container.getRestAddress()));
    final CamundaClient client = builder.build();
    clients.add(client);

    return client;
  }
}
