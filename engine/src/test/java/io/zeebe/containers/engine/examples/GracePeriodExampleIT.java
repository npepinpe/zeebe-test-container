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
import io.zeebe.containers.engine.ContainerEngine;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * This example shows how setting a grace period to your engine via {@link
 * ContainerEngine.Builder#withGracePeriod(Duration)} can be a good alternative to using {@link
 * io.camunda.zeebe.process.test.api.ZeebeTestEngine#waitForIdleState(Duration)}, as you do not need
 * to call it ever.
 *
 * <p>NOTE: one of the pitfalls with this method however is that certain assertions, notably those
 * which check for the <em>absence</em> of something, will typically block for the complete duration
 * of the grace period, thus slowing down your tests.
 */
@Testcontainers
final class GracePeriodExampleIT {
  @Container
  private final ContainerEngine engine =
      ContainerEngine.builder().withGracePeriod(Duration.ofSeconds(5)).build();

  @Test
  void shouldCompleteProcessInstance() {
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
    BpmnAssert.assertThat(processInstance).isStarted().isCompleted();
  }
}
