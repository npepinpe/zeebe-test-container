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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.condition.OS.LINUX;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.containers.util.TestSupport;
import io.zeebe.containers.util.TestcontainersSupport.DisabledIfTestcontainersCloud;
import io.zeebe.containers.util.TopologyAssert;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

final class ZeebeBrokerNodeTest {
  @AutoClose private static final Network NETWORK = Network.newNetwork();

  private static ZeebeBrokerNode<?> provideBrokerWithHostData(
      final ZeebeBrokerNode<?> broker, final Path tmpDir) throws IOException {
    // configure the broker to use the same UID and GID as our current user so we can remove the
    // temporary directory at the end. Note that this is only necessary when not running the tests
    // as root
    Files.createDirectories(tmpDir.resolve("data"));
    Files.createDirectories(tmpDir.resolve("logs"));

    final ZeebeHostData data =
        new ZeebeHostData(tmpDir.resolve("data").toAbsolutePath().toString());
    final ZeebeHostData logs =
        new ZeebeHostData(
            tmpDir.resolve("logs").toAbsolutePath().toString(),
            ZeebeDefaults.getInstance().getDefaultLogsPath());

    // when
    final String runAsUser = TestSupport.getRunAsUser();
    broker
        .withZeebeData(data)
        .withZeebeData(logs)
        .self()
        .withCreateContainerCmdModifier(cmd -> cmd.withUser(runAsUser));

    return broker;
  }

  @SuppressWarnings("resource")
  private static Stream<Arguments> reuseDataTestCases() {
    return Stream.of(
        new ReuseDataTestCase(
            "broker with embedded gateway should reuse host data",
            path -> provideBrokerWithHostData(new ZeebeContainer(), path).withNetwork(NETWORK)),
        new ReuseDataTestCase(
            "broker without embedded gateway should reuse host data",
            path ->
                provideBrokerWithHostData(new ZeebeBrokerContainer(), path).withNetwork(NETWORK)),
        new ReuseDataTestCase(
            "broker with embedded gateway should reuse volume",
            path ->
                new ZeebeContainer().withZeebeData(ZeebeVolume.newVolume()).withNetwork(NETWORK)),
        new ReuseDataTestCase(
            "broker without embedded gateway should reuse volume",
            path ->
                new ZeebeBrokerContainer()
                    .withZeebeData(ZeebeVolume.newVolume())
                    .withNetwork(NETWORK)));
  }

  @SuppressWarnings("resource")
  private static Stream<Arguments> nodeProvider() {
    final Stream<ZeebeBrokerNode<?>> nodes =
        Stream.of(
            new ZeebeContainer().withNetwork(NETWORK),
            new ZeebeBrokerContainer().withNetwork(NETWORK));
    return nodes.map(node -> Arguments.of(node.getClass().getSimpleName(), node));
  }

  @SuppressWarnings({"unused", "HttpUrlsUsage"})
  @Timeout(value = 5, unit = TimeUnit.MINUTES)
  @ParameterizedTest(name = "{0} should be ready on start")
  @MethodSource("nodeProvider")
  void shouldBeReadyOnStart(
      @SuppressWarnings("unused") final String testName, final ZeebeBrokerNode<?> node)
      throws IOException {
    // given
    final int statusCode;
    try (final GenericContainer<?> container = node.self()) {
      container.start();

      // when
      final URL monitoringUrl =
          new URL(String.format("http://%s/ready", node.getExternalMonitoringAddress()));
      final HttpURLConnection connection = (HttpURLConnection) monitoringUrl.openConnection();
      try {
        connection.connect();
        statusCode = connection.getResponseCode();
      } finally {
        connection.disconnect();
      }
    }

    // then
    assertThat(statusCode)
        .as("the broker ready check should return 2xx when the container is started")
        .isBetween(200, 299);
  }

  @ParameterizedTest(name = "{0} should expose all ports except gateway")
  @MethodSource("nodeProvider")
  void shouldExposeAllPortsButGateway(
      @SuppressWarnings("unused") final String testName, final ZeebeBrokerNode<?> node) {
    // given
    final List<Integer> expectedPorts =
        Arrays.stream(ZeebePort.values()).map(ZeebePort::getPort).collect(Collectors.toList());
    final List<Integer> gatewayPorts =
        Arrays.asList(ZeebePort.GATEWAY_GRPC.getPort(), ZeebePort.GATEWAY_REST.getPort());
    expectedPorts.removeAll(gatewayPorts);

    // when
    final List<Integer> exposedPorts = node.getExposedPorts();

    // then
    assertThat(exposedPorts)
        .as("the broker should expose all the ports but the gateway")
        .containsAll(expectedPorts);
  }

  @Timeout(value = 5, unit = TimeUnit.MINUTES)
  @ParameterizedTest(name = "{0}")
  @MethodSource("reuseDataTestCases")
  @EnabledOnOs(LINUX)
  @DisabledIfTestcontainersCloud
  void shouldReuseHostDataOnRestart(
      @SuppressWarnings("unused") final String testName,
      final BrokerNodeProvider brokerNodeProvider,
      final @TempDir Path tempDir)
      throws Exception {
    // given
    try (final ZeebeBrokerNode<?> broker = brokerNodeProvider.apply(tempDir);
        final ZeebeGatewayContainer gateway =
            new ZeebeGatewayContainer()
                .withNetwork(NETWORK)
                .withEnv(
                    "ZEEBE_GATEWAY_CLUSTER_CONTACTPOINT", broker.getInternalClusterAddress())) {

      // when
      broker.start();
      gateway.start();

      try (final CamundaClient client = TestSupport.newZeebeClient(gateway)) {
        // deploy a new process, which we can use on restart to assert that the data was correctly
        // reused
        final DeploymentEvent deployment = deploySampleProcess(client);
        broker.stop();

        // on restart, we need to wait until the gateway is aware of the new leader
        broker.start();
        awaitUntilTopologyIsComplete(client);
        final ProcessInstanceEvent processInstance = createSampleProcessInstance(client);

        // then
        assertThat(processInstance)
            .as("the process instance was successfully created")
            .isNotNull()
            .extracting(ProcessInstanceEvent::getProcessDefinitionKey)
            .isEqualTo(deployment.getProcesses().get(0).getProcessDefinitionKey());
      }
    }
  }

  private ProcessInstanceEvent createSampleProcessInstance(final CamundaClient client) {
    return client.newCreateInstanceCommand().bpmnProcessId("process").latestVersion().send().join();
  }

  private DeploymentEvent deploySampleProcess(final CamundaClient client) {
    final BpmnModelInstance sampleProcess =
        Bpmn.createExecutableProcess("process").startEvent().endEvent().done();
    return client
        .newDeployResourceCommand()
        .addProcessModel(sampleProcess, "process.bpmn")
        .send()
        .join();
  }

  private void awaitUntilTopologyIsComplete(final CamundaClient client) {
    Awaitility.await("until topology is complete")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                TopologyAssert.assertThat(client.newTopologyRequest().send().join())
                    .isComplete(1, 1, 1));
  }

  @FunctionalInterface
  private interface BrokerNodeProvider {
    ZeebeBrokerNode<?> apply(final Path tmpDir) throws Exception;
  }

  private static final class ReuseDataTestCase implements Arguments {
    private final String testName;
    private final BrokerNodeProvider brokerProvider;

    public ReuseDataTestCase(final String testName, final BrokerNodeProvider brokerProvider) {
      this.testName = testName;
      this.brokerProvider = brokerProvider;
    }

    @Override
    public Object[] get() {
      return new Object[] {testName, brokerProvider};
    }
  }
}
