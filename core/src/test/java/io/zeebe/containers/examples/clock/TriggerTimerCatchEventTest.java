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
package io.zeebe.containers.examples.clock;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.containers.ZeebeContainer;
import io.zeebe.containers.clock.ZeebeClock;
import io.zeebe.containers.util.TestSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * This example showcases how to test triggering a timer catch event by modifying the broker's actor
 * clock once the instance has been created. This is done by deploying a simple process which has a
 * start event, followed by a timer catch event, then a service task, and finally an end event. The
 * timer catch event is set to trigger one day from now. Additionally, a worker is set up from the
 * start to poll for the task immediately following the catch event.
 *
 * <p>Once deployed, the broker's actor clock is advanced by one day, and we can verify that one job
 * was activated by the worker - this means that the instance has passed the timer catch event.
 */
@Testcontainers
final class TriggerTimerCatchEventTest {
  @AutoClose private static final Network NETWORK = Network.newNetwork();

  private static final String JOB_TYPE = "type";
  private static final Duration TIME_OFFSET = Duration.ofDays(1);
  private static final Instant TIMER_DATE = Instant.now().plus(TIME_OFFSET);

  @Container
  private final ZeebeContainer zeebeContainer =
      new ZeebeContainer().withNetwork(NETWORK).withEnv("ZEEBE_CLOCK_CONTROLLED", "true");

  @Test
  @Timeout(value = 5, unit = TimeUnit.MINUTES)
  void shouldTriggerTimerStartEvent() {
    // given
    final ZeebeClock clock = ZeebeClock.newDefaultClock(zeebeContainer);
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent()
            .timerWithDate(TIMER_DATE.toString())
            .serviceTask("task", b -> b.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();
    final List<ActivatedJob> activatedJobs = new CopyOnWriteArrayList<>();
    final Instant brokerTime;

    // when
    final JobHandler handler = (client, job) -> activatedJobs.add(job);
    try (final CamundaClient client = TestSupport.newZeebeClient(zeebeContainer);
        final JobWorker ignored = newJobWorker(handler, client)) {
      client.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send().join();
      client.newCreateInstanceCommand().bpmnProcessId("process").latestVersion().send().join();
      brokerTime = clock.addTime(TIME_OFFSET);
      Awaitility.await("until a job has been activated by the worker")
          .untilAsserted(() -> Assertions.assertThat(activatedJobs).hasSize(1));
    }

    // then
    Assertions.assertThat(activatedJobs)
        .as("the timer event was triggered and a job is now available")
        .hasSize(1);
    Assertions.assertThat(brokerTime)
        .as("the modified time is at least equal to one day from now")
        .isAfterOrEqualTo(TIMER_DATE);
  }

  private JobWorker newJobWorker(final JobHandler handler, final CamundaClient client) {
    return client.newWorker().jobType(JOB_TYPE).handler(handler).open();
  }
}
