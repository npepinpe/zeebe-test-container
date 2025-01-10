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

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.Topology;
import io.zeebe.containers.util.TestSupport;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
final class ZeebeContainerTest {
  @Container private final ZeebeContainer zeebeContainer = new ZeebeContainer();

  @Test
  @Timeout(value = 5, unit = TimeUnit.MINUTES)
  void shouldStartWithEmbeddedGatewayByDefault() {
    // given
    final Topology topology;

    // when
    try (final CamundaClient client = TestSupport.newZeebeClient(zeebeContainer)) {
      topology = client.newTopologyRequest().send().join(5, TimeUnit.SECONDS);
    }

    // then
    // as we're not testing the client/gateway themselves, it's fine to simply assert we get a
    // successful response here  - the property we assert isn't too important
    Assertions.assertThat(topology.getGatewayVersion())
        .as("the gateway is started")
        .isEqualTo(ZeebeDefaults.getInstance().getDefaultVersion());
  }
}
