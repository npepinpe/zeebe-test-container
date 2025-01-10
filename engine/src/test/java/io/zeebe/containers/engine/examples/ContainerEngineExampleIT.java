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
import io.zeebe.containers.ZeebeContainer;
import io.zeebe.containers.engine.ContainerEngine;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * This example showcases how to set up a {@link io.camunda.zeebe.process.test.api.ZeebeTestEngine}
 * which points to a pre-configured {@link io.zeebe.containers.ZeebeContainer}.
 *
 * <p>Note that the lifecycle of the configured container is managed by the {@link ContainerEngine}.
 * As such, you will notice the {@code engine} field is annotated with {@link Container} and not the
 * container itself.
 *
 * <p>For a more complete example of how to use {@link
 * io.camunda.zeebe.process.test.api.ZeebeTestEngine} and {@link
 * io.camunda.zeebe.process.test.assertions.BpmnAssert}, refer to <a
 * href="https://github.com/camunda/zeebe-process-test">zeebe-process-test</a>.
 */
@Testcontainers
final class ContainerEngineExampleIT {
  private static final Logger LOGGER = LoggerFactory.getLogger(ContainerEngineExampleIT.class);

  // a container which will print out its log to the given logger
  private final ZeebeContainer container =
      new ZeebeContainer().withLogConsumer(new Slf4jLogConsumer(LOGGER));

  @Container
  private final ContainerEngine engine =
      ContainerEngine.builder()
          .withContainer(container)
          .withIdlePeriod(Duration.ofSeconds(2))
          .build();

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
