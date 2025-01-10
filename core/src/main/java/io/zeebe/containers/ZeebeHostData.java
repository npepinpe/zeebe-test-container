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

import org.apiguardian.api.API;
import org.apiguardian.api.API.Status;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;

/**
 * A {@link ZeebeData} implementations which will mount a host path to the default Zeebe data path
 * for a broker container. See {@link ZeebeDefaults#getDefaultDataPath()} for more.
 *
 * <p>NOTE: keep in mind that files will be written using the container's user. This means, if you
 * run it as root, it may create files that your use cannot delete afterward.
 *
 * <p>To work around this, on Linux, make sure to start your container with your user ID and group
 * ID. e.g.:
 *
 * <pre>{@code
 * new ZeebeContainer()
 *   .withZeebeData(new ZeebeHostData("/tmp/data"))
 *   .withCreateCmdModifier(cmd -> cmd.withUser("1000:1000"))
 *   .start();
 * }</pre>
 *
 * Another option is to run a command on the container, before deleting everything, to change
 * permissions such that all files are writable by everyone.
 *
 * <p>NOTE: be careful when using this. It's not easy to remove a file system bind via the normal
 * Testcontainers API. So assume that if you attach this to a container, it's attached forever. If
 * you want to swap data over time, use a {@link ZeebeVolume}.
 */
@API(status = Status.EXPERIMENTAL)
public class ZeebeHostData implements ZeebeData {
  private final String hostPath;
  private final String mountPath;

  /**
   * Creates a new {@link ZeebeHostData} which points to the given host path.
   *
   * @param hostPath the path where the data will be stored on the host
   */
  public ZeebeHostData(final String hostPath) {
    this(hostPath, ZeebeDefaults.getInstance().getDefaultDataPath());
  }

  /**
   * Creates a new {@link ZeebeHostData} which points to the given host path.
   *
   * @param hostPath the path where the data will be stored on the host
   */
  public ZeebeHostData(final String hostPath, final String mountPath) {
    this.hostPath = hostPath;
    this.mountPath = mountPath;
  }

  @Override
  public <T extends GenericContainer<T> & ZeebeBrokerNode<T>> void attach(final T container) {
    container.withFileSystemBind(hostPath, mountPath, BindMode.READ_WRITE);
  }
}
