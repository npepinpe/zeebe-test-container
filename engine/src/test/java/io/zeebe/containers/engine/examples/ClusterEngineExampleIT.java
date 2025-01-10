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
package io.zeebe.containers.engine.examples;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.process.test.assertions.BpmnAssert;
import io.zeebe.containers.cluster.ZeebeCluster;
import io.zeebe.containers.engine.ContainerEngine;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
final class ClusterEngineExampleIT {
  private final Network network = Network.newNetwork();

  // a container which will print out its log to the given logger
  private final ZeebeCluster cluster =
      ZeebeCluster.builder()
          .withGatewaysCount(1)
          .withPartitionsCount(2)
          .withReplicationFactor(2)
          .withBrokersCount(2)
          .withEmbeddedGateway(false)
          .withNetwork(network)
          .build();

  @Container
  private final ContainerEngine engine =
      ContainerEngine.builder().withCluster(cluster).withIdlePeriod(Duration.ofSeconds(2)).build();

  @AfterEach
  void afterEach() {
    network.close();
  }

  @Test
  void shouldCompleteProcessInstance() throws InterruptedException, TimeoutException {
    // given
    final BpmnModelInstance processModel =
        Bpmn.createExecutableProcess("process").startEvent().endEvent().done();
    final ProcessInstanceEvent processInstance;

    // when
    try (final CamundaClient client = engine.createClient()) {
      client.newDeployResourceCommand().addProcessModel(processModel, "process.bpmn").send().join();
      processInstance =
          client.newCreateInstanceCommand().bpmnProcessId("process").latestVersion().send().join();
    }

    // then
    engine.waitForIdleState(Duration.ofSeconds(5));
    BpmnAssert.assertThat(processInstance).isStarted().isCompleted();
  }
}
