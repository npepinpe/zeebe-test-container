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
package io.zeebe.containers.examples.exporter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.zeebe.containers.ZeebeContainer;
import io.zeebe.containers.exporter.DebugReceiver;
import io.zeebe.containers.util.TestSupport;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.assertj.core.groups.Tuple;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * An example of using the debug exporter to extract events from the Zeebe log. Here we will simply
 * publish a message, which produces exactly two records (one command and one corresponding event).
 *
 * <p>The goal of the test is to showcase extracting the events, and not their correctness.
 *
 * <p>You will notice the test uses {@link Awaitility} - this is because a Zeebe broker, by design,
 * is asynchronous. As such, it takes a variable amount of time between publishing the message until
 * the records are exported. This is unfortunately necessary for now.
 *
 * <p>Also note that we start the {@link DebugReceiver} instance first, before the container. This
 * is required as we want to grab a random port, and only after starting the server can we know what
 * the port is exactly. If you wish to use a fix port, you can use {@link
 * DebugReceiver#DebugReceiver(Consumer, int)}, and pass the fixed port to {@link
 * io.zeebe.containers.ZeebeBrokerNode#withDebugExporter(int)}.
 */
@Testcontainers
final class BrokerWithDebugExporterIT {
  private final List<Record<?>> records = new CopyOnWriteArrayList<>();
  private final DebugReceiver receiver = new DebugReceiver(records::add).start();

  @Container
  private final ZeebeContainer container =
      new ZeebeContainer().withDebugExporter(receiver.serverAddress().getPort());

  @AfterEach
  void afterEach() {
    receiver.close();
  }

  @Test
  void shouldReadExportedRecords() {
    // given
    try (final CamundaClient client = TestSupport.newZeebeClient(container)) {

      // when
      client
          .newPublishMessageCommand()
          .messageName("myMessage")
          .correlationKey("myKey")
          .send()
          .join();
    }

    // then
    Awaitility.await("until there's at least some records exported")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(records)
                    .extracting(Record::getValueType, Record::getIntent)
                    .containsExactly(
                        Tuple.tuple(ValueType.MESSAGE, MessageIntent.PUBLISH),
                        Tuple.tuple(ValueType.MESSAGE, MessageIntent.PUBLISHED)));
  }
}
