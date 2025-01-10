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
package io.zeebe.containers.examples;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.containers.ZeebeContainer;
import io.zeebe.containers.ZeebeVolume;
import io.zeebe.containers.util.TestSupport;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * This example showcases how to create a simple test against a single node broker where data is
 * kept across restarts.
 *
 * <p>To validate this, we deploy a process, restart the node, and then create an instance. If the
 * data was not kept, then the original process wouldn't have been deployed.
 */
@Testcontainers
final class ReusableVolumeExampleTest {
  @AutoClose private static final Network NETWORK = Network.newNetwork();

  private final ZeebeVolume volume = ZeebeVolume.newVolume();

  @Container
  private final ZeebeContainer zeebeContainer =
      new ZeebeContainer().withZeebeData(volume).withNetwork(NETWORK);

  @Test
  @Timeout(value = 5, unit = TimeUnit.MINUTES)
  void shouldReuseVolume() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process").startEvent().endEvent().done();

    // when
    try (final CamundaClient client = TestSupport.newZeebeClient(zeebeContainer)) {
      client.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send().join();
    }

    // restart
    zeebeContainer.stop();
    zeebeContainer.start();

    // create a process instance from the one we previously deployed - this would fail if we hadn't
    // previously deployed our process model
    final ProcessInstanceEvent processInstance;
    try (final CamundaClient client = TestSupport.newZeebeClient(zeebeContainer)) {
      processInstance =
          client.newCreateInstanceCommand().bpmnProcessId("process").latestVersion().send().join();
    }

    // then
    assertThat(processInstance.getProcessInstanceKey())
        .as("a process instance was successfully created")
        .isPositive();
  }
}
