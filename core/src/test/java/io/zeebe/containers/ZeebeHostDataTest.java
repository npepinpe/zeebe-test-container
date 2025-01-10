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

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse.Mount;
import io.zeebe.containers.util.TestSupport;
import io.zeebe.containers.util.TestcontainersSupport.DisabledIfTestcontainersCloud;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.DockerClientFactory;

@EnabledOnOs(LINUX)
@DisabledIfTestcontainersCloud
final class ZeebeHostDataTest {
  @SuppressWarnings("resource")
  @Test
  void shouldAttachToZeebeContainer(final @TempDir Path tmpDir) throws IOException {
    // given
    // configure the broker to use the same UID and GID as our current user so we can remove the
    // temporary directory at the end. Note that this is only necessary when not running the tests
    // as root
    final DockerClient client = DockerClientFactory.lazyClient();
    final String runAsUser = TestSupport.getRunAsUser();
    final Path dataDir = tmpDir.resolve("data");
    Files.createDirectories(dataDir);

    // create logs directory to avoid errors since we'll be running with a different user than the
    // usual "camunda:root"
    Files.createDirectories(tmpDir.resolve("logs"));

    // when
    final InspectContainerResponse response;
    try (final ZeebeBrokerContainer container = new ZeebeBrokerContainer()) {
      final ZeebeHostData data = new ZeebeHostData(dataDir.toAbsolutePath().toString());
      final ZeebeHostData logs =
          new ZeebeHostData(
              tmpDir.resolve("logs").toAbsolutePath().toString(),
              ZeebeDefaults.getInstance().getDefaultLogsPath());

      // configure the broker to use the same UID and GID as our current user, so we can remove the
      // temporary directory at the end
      container
          .withZeebeData(data)
          .withZeebeData(logs)
          .self()
          .withCreateContainerCmdModifier(cmd -> cmd.withUser(runAsUser));
      container.start();

      response = client.inspectContainerCmd(container.getContainerId()).exec();
    }

    // then
    assertThat(response.getMounts()).as("our volume mount should be there").isNotEmpty();

    final String containerPath = ZeebeDefaults.getInstance().getDefaultDataPath();
    final Mount mount =
        response.getMounts().stream()
            .filter(m -> m.getDestination() != null)
            .filter(m -> containerPath.equals(m.getDestination().getPath()))
            .findFirst()
            .orElseThrow(
                () ->
                    new AssertionError(
                        "No mounts for data volume found in: " + response.getMounts()));
    assertThat(mount.getRW()).as("the host data should be mounted as read-write").isTrue();
    assertThat(mount.getSource())
        .as("the mount's source should be the host data directory")
        .isEqualTo(dataDir.toString());
  }
}
