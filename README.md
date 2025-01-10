# Zeebe Test Container

[![](https://img.shields.io/badge/Community%20Extension-An%20open%20source%20community%20maintained%20project-FF4700)](https://github.com/camunda-community-hub/community)
[![](https://img.shields.io/badge/Lifecycle-Incubating-blue)](https://github.com/Camunda-Community-Hub/community/blob/main/extension-lifecycle.md#incubating-)
[![](https://img.shields.io/github/v/release/zeebe-io/zeebe-test-container?sort=semver)](https://github.com/zeebe-io/zeebe-test-container/releases/latest)
[![Java CI](https://github.com/zeebe-io/zeebe-test-container/actions/workflows/ci.yml/badge.svg)](https://github.com/zeebe-io/zeebe-test-container/actions/workflows/ci.yml?branch=main)
![Compatible with: Camunda Platform 8](https://img.shields.io/badge/Compatible%20with-Camunda%20Platform%208-0072Ce)

Easily test your application against a containerized, configurable Zeebe instance.

Please refer to [testcontainers.org](https://testcontainers.org) for general documentation on how to
use containers for your tests, as well as general prerequisites.

- [Zeebe Test Container](#zeebe-test-container)
  - [When to use this project?](#when-to-use-this-project)
  - [Supported Zeebe versions](#supported-zeebe-versions)
  - [Installation](#installation)
    - [Requirements](#requirements)
  - [Compatibility guarantees](#compatibility-guarantees)
  - [Quickstart](#quickstart)
  - [Using with junit4](#using-with-junit4)
  - [Using with junit5](#using-with-junit5)
- [Usage](#usage)
  - [Broker with embedded gateway](#broker-with-embedded-gateway)
  - [Standalone broker without gateway](#standalone-broker-without-gateway)
  - [Standalone gateway](#standalone-gateway)
  - [Configuring your container](#configuring-your-container)
  - [Examples](#examples)
  - [Continuous Integration](#continuous-integration)
- [Experimental features](#experimental-features)
  - [Cluster](#cluster)
    - [Usage](#usage-1)
    - [Examples](#examples-1)
    - [Cluster startup time](#cluster-startup-time)
  - [Debugging](#debugging)
  - [Volumes and data](#volumes-and-data)
  - [Extracting data](#extracting-data)
  - [Time traveling](#time-traveling)
  - [Debug exporter](#debug-exporter)
  - [Zeebe Process Test Compatibility](#zeebe-process-test-compatibility)
- [Tips](#tips)
  - [Tailing your container's logs during development](#tailing-your-containers-logs-during-development)
  - [Configuring GenericContainer specific properties with a Zeebe*Node interface](#configuring-genericcontainer-specific-properties-with-a-zeebenode-interface)
  - [Advanced Docker usage via the DockerClient](#advanced-docker-usage-via-the-dockerclient)
    - [Gracefully restarting a container](#gracefully-restarting-a-container)
  - [Limiting container resources](#limiting-container-resources)
- [Contributing](#contributing)
  - [Build from source](#build-from-source)
    - [Prerequisites](#prerequisites)
    - [Building](#building)
  - [Backwards compatibility](#backwards-compatibility)
  - [Report issues or contact developers](#report-issues-or-contact-developers)
  - [Create a Pull Request](#create-a-pull-request)
  - [Commit Message Guidelines](#commit-message-guidelines)
  - [Contributor License Agreement](#contributor-license-agreement)

## When to use this project?

In most cases, you probably do not need to use this project, and instead want to
use [zeebe-process-test](https://github.com/camunda/zeebe-process-test).

`zeebe-process-test` is built to allow you to write unit and integration tests of your
BPMN process definitions, DMN models, and how they interact together with your workers. It provides
a stripped down Zeebe engine, one which fully supports the same BPMN and DMN features as
Zeebe, but without the administrative capabilities and other advanced features (e.g. multiple
partitions, backups, rebalancing, monitoring, etc.)

If all you want to do is test the correctness of your BPMN process definitions or DMN models, and
how they interact with your workers, then stick with `zeebe-process-test`. However, if you want to
do any of the following, the `zeebe-test-container` is the right tool:

- write acceptance/integration tests for Zeebe itself
- write acceptance/integration tests for a Zeebe gateway interceptor
- write acceptance/integration tests for a Zeebe exporter
- write automated acceptance tests that use advanced features (e.g. parallelism across multiple
  partitions, automated rebalancing of leadership, chaos/resilience tests, etc.)

**This tool is not made for load testing, as you will most likely not achieve the same level of
performance as you would in a production deployment.**

## Supported Zeebe versions

> **NOTE**: version 1.0 is incompatible with Zeebe versions pre 0.23.x

Version 1.x and 2.x is compatible with the following Zeebe versions:

- 0.23.x
- 0.24.x
- 0.25.x
- 0.26.x

Version 3.x is compatible with the following Zeebe versions:

- 1.x
- 8.x

## Installation

Add the project to your dependencies:

```xml

<dependency>
  <groupId>io.zeebe</groupId>
  <artifactId>zeebe-test-container</artifactId>
  <version>3.5.0</version>
</dependency>
```

```groovy
testImplementation 'io.zeebe:zeebe-test-container:3.5.0'
```

### Requirements

Zeebe Test Container is built for Java 8+, and will not work on lower Java versions.

> You will need Java 21+ for development purposes, however, as many of our tests rely on shared
> Zeebe libraries which are built for Java 21+.

Additionally, you will need to comply with all the Testcontainers requirements, as defined
[here](https://www.testcontainers.org/#prerequisites).

## Compatibility guarantees

As there is currently only a single maintainer, only the latest major version will be maintained and
supported.

`zeebe-test-container` uses [API guardian](https://github.com/apiguardian-team/apiguardian) to
declare the stability and guarantees of its API.

Every class/interface/etc. is annotated with an `@API` annotation describing its status.
Additionally, at times, inner members of a class/interface/etc. may be annotated with an
overriding `@API`
annotation. In that case, it would take precedence over its parent annotation. For example, if you
have the following:

```java
import org.apiguardian.api.API;
import org.apiguardian.api.API.Status;

@API(status = Status.STABLE)
public class MyClass {

  @API(status = Status.EXPERIMENTAL)
  public void myExperimentalMethod() {

  }

  public void myStableMethod() {

  }
}
```

Then we can assume the contract for `MyClass` is stable and will not be changed until the next major
version, __except__ for its method `myExperimentalMethod`, which is an experimental addition which
may change at any time, at least until it is marked as stable or dropped.

> NOTE: for contributors, please remember to annotate new additions, and to maintain the
> compatibility guarantees of pre-annotated entities.

## Quickstart

## Using with junit4

If you're using junit4, you can add the container as a rule: it will be started and closed around
each test execution. You can read more about Testcontainers and
junit4 [here](https://www.testcontainers.org/test_framework_integration/junit_4/).

```java
package com.acme.zeebe;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.ProcessInstanceResult;
import io.zeebe.containers.ZeebeContainer;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;

public class MyFeatureTest {

  @Rule
  public ZeebeContainer zeebeContainer = new ZeebeContainer();

  @Test
  public void shouldConnectToZeebe() {
    // given
    final CamundaClient client =
      CamundaClient.newClientBuilder()
        .grpcAddress(zeebeContainer.getGrpcAddress())
        .restAddress(zeebeContainer.getRestAddress())
        .usePlaintext()
        .build();
    final BpmnModelInstance process =
      Bpmn.createExecutableProcess("process").startEvent().endEvent().done();

    // when
    // do something (e.g. deploy a process)
    final DeploymentEvent deploymentEvent =
      client.newDeployCommand().addProcessModel(process, "process.bpmn").send().join();

    // then
    // verify (e.g. we can create an instance of the deployed process)
    final ProcessInstanceResult processInstanceResult =
      client
        .newCreateInstanceCommand()
        .bpmnProcessId("process")
        .latestVersion()
        .withResult()
        .send()
        .join();
    Assertions.assertThat(processInstanceResult.getProcessDefinitionKey())
      .isEqualTo(deploymentEvent.getProcesses().get(0).getProcessDefinitionKey());
  }
}
```

## Using with junit5

If you're using junit5, you can use the `Testcontainers` extension. It will manage the container
lifecycle for you. You can read more about the
extension [here](https://www.testcontainers.org/test_framework_integration/junit_5/).

```java
package com.acme.zeebe;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.ProcessInstanceResult;
import io.zeebe.containers.ZeebeContainer;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class MyFeatureTest {

  @Container
  private final ZeebeContainer zeebeContainer = new ZeebeContainer();

  @Test
  void shouldConnectToZeebe() {
    // given
    final CamundaClient client =
      CamundaClient.newClientBuilder()
        .grpcAddress(zeebeContainer.getGrpcAddress())
        .restAddress(zeebeContainer.getRestAddress())
        .usePlaintext()
        .build();
    final BpmnModelInstance process =
      Bpmn.createExecutableProcess("process").startEvent().endEvent().done();

    // when
    // do something (e.g. deploy a process)
    final DeploymentEvent deploymentEvent =
      client.newDeployCommand().addProcessModel(process, "process.bpmn").send().join();

    // then
    // verify (e.g. we can create an instance of the deployed process)
    final ProcessInstanceResult processInstanceResult =
      client
        .newCreateInstanceCommand()
        .bpmnProcessId("process")
        .latestVersion()
        .withResult()
        .send()
        .join();
    Assertions.assertThat(processInstanceResult.getProcessDefinitionKey())
      .isEqualTo(deploymentEvent.getProcesses().get(0).getProcessDefinitionKey());
  }
}
```

# Usage

`zeebe-test-container` provides three different containers:

- `ZeebeContainer`: a Zeebe broker with an embedded gateway
- `ZeebeBrokerContainer`: a Zeebe broker without an embedded gateway
- `ZeebeGatewayContainer`: a standalone Zeebe gateway
- `ZeebeCluster`: an experimental cluster builder API which lets you manage a Zeebe cluster made of
  various containers

> If you're unsure which one you should use, then you probably want to use `ZeebeContainer`, as it
> is the quickest way to test your application against Zeebe.

## Broker with embedded gateway

`ZeebeContainer` will start a new Zeebe broker with embedded gateway. For most tests, this is what
you will want to use. It provides all the functionality of a Zeebe single node deployment, which for
testing purposes should be enough.

The container is considered started if and only if:

1. The monitoring, command, cluster, and gateway ports are open and accepting connections (read more
   about the
   ports [here](https://docs.camunda.io/docs/components/zeebe/deployment-guide/operations/network-ports/))
   .
2. The broker ready check returns a 204 (see more about this
   check [here](https://docs.camunda.io/docs/components/zeebe/deployment-guide/operations/health/#ready-check))
   .
3. The gateway topology is considered complete.

> A topology is considered complete if there is a leader for all partitions.

Once started, the container is ready to accept commands, and a client can connect to it by setting
its `grpcAddress` to `ZeebeContainer#getGrpcAddress()`, and its `restAddress` to `ZeebeContainer#getRestAddress()`.

## Standalone broker without gateway

`ZeebeBrokerContainer` will start a new Zeebe broker with no embedded gateway. As it contains no
gateway, the use case for this container is to test Zeebe in clustered mode. As such, it will
typically be combined with a `ZeebeGatewayContainer` or a `ZeebeContainer`.

The container is considered started if and only if:

1. The monitoring, command, and cluster ports are open and accepting connections (read more about
   the
   ports [here](https://docs.camunda.io/docs/components/zeebe/deployment-guide/operations/network-ports/))
   .
2. The broker ready check returns a 204 (see more about this
   check [here](https://docs.camunda.io/docs/components/zeebe/deployment-guide/operations/health/#ready-check))
   .

Once started, the container is ready to accept commands via the command port; you should therefore
link a gateway to it if you wish to use it.

## Standalone gateway

`ZeebeGatewayContainer` will start a new Zeebe standalone gateway. As it is only a gateway, it
should be linked to at least one broker - a `ZeebeContainer` or `ZeebeBrokerContainer`. By default,
it will not expose the monitoring port, as monitoring is not enabled in the gateway by default. If
you enable monitoring, remember to expose the port as well via
`GenericContainer#addExposedPort(int)`.

The container is considered started if and only if:

1. The cluster and gateway ports are open and accepting connections (read more about the
   ports [here](https://docs.camunda.io/docs/components/zeebe/deployment-guide/operations/network-ports/))
   .
2. The gateway topology is considered complete.

> A topology is considered complete if there is a leader for all partitions.

Once started, the container is ready to accept commands, and a client can connect to it by setting
its `grpcAddress` to `ZeebeContainer#getGrpcAddress()`, and its `restAddress` to `ZeebeContainer#getRestAddress()`.

## Configuring your container

Configuring your Zeebe container of choice is done exactly as you normally would - via environment
variables or via configuration file. You can find out more about it on the
[Zeebe documentation website](https://docs.camunda.io/docs/components/zeebe/deployment-guide/configuration/configuration/)
.

> Zeebe 0.23.x and upwards use Spring Boot for configuration - refer to their documentation on how
> environment variables are mapped to configuration settings. You can read more about this
> [here](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-external-config)
>
> Testcontainers provide mechanisms through which
> [environment variables can be injected](https://www.javadoc.io/doc/org.testcontainers/testcontainers/1.14.3/org/testcontainers/containers/GenericContainer.html#withEnv-java.lang.String-java.lang.String-)
> ,
> or [configuration files mounted](https://www.testcontainers.org/features/files/). Refer to their
> documentation for more.

## Examples

A series of examples are included as part of the tests, see
[test/java/io/zeebe/containers/examples](/core/src/test/java/io/zeebe/containers/examples).

> Note that these are written for junit5.

Below you will find examples which are not part of the project itself, mostly due to requiring
external resources or systems.

### Testing a custom exporter

You can use `zeebe-test-container` to test your custom exporter. There are two ways to go about
this: with a pre-built Docker image containing your exporter, or by loading a pre-packaged JAR
containing your exporter.

#### Loading exporter JAR

The idea here is to load your custom exporter, pre-packaged into a self-contained JAR, either as a
[file or a classpath resource](https://java.testcontainers.org/features/files/#copying-to-a-container-before-startup).

Here are some projects which do exactly this:

- [Kafka Exporter](https://github.com/camunda-community-hub/zeebe-kafka-exporter/blob/main/qa/src/test/java/io/zeebe/exporters/kafka/qa/KafkaExporterIT.java)
- [ClickHouse Exporter](https://github.com/camunda-community-hub/zeebe-clickhouse-exporter/blob/main/src/test/java/io/zeebe/clickhouse/exporter/ClickHouseExporterIT.java)

You can do this in two ways:

1. Ensure the artifact is packaged separately and made available via the file system
2. Hook into your build pipeline to always have the latest JAR available to your QA tests

The second option is more work, but it provides the better experience as running `mvn verify` for
your QA module will always run your tests with the latest code changes seamlessly.

##### Build and test the exporter JAR

You can do this by creating a second module or project specifically to run integration tests for
your exporter.

> [!Note]
> The following assumes you're using Maven, but it should be possible to adapt it to
> Gradle.

First, create the new project, say, `exporter-qa`. It will need to list your exporter module, say
`exporter`, as a dependency. This will ensure that the `exporter` module is built before
`exporter-qa`.

For example:

```xml
<dependencies>
  <!-- ensure the exporter project is built before the QA project -->
  <dependency>
    <groupId>com.acme</groupId>
    <artifactId>exporter</artifactId>
    <scope>provided</scope>
    <optional>true</optional>
  </dependency>
</dependencies>
```

Next, we need to copy the packaged JAR of the `exporter` module into the `exporter-qa` module:

```xml
<build>
  <plugins>
    <!-- copy over the exporter JAR as a resource -->
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-dependency-plugin</artifactId>
      <version>${plugin.version.dependency}</version>
      <configuration>
        <!-- you may require this if you aren't actually referencing the exporter itself -->
        <ignoredUnusedDeclaredDependencies>
          <dep>com.acme:exporter</dep>
        </ignoredUnusedDeclaredDependencies>
      </configuration>
      <dependencies>
        <!-- Fake dependency on the exporter to ensure it's packaged beforehand -->
        <dependency>
          <groupId>com.acme</groupId>
          <artifactId>exporter</artifactId>
          <version>${project.version}</version>
        </dependency>
      </dependencies>
      <executions>
        <execution>
          <id>copy</id>
          <goals>
            <goal>copy</goal>
          </goals>
          <phase>package</phase>
          <configuration>
            <artifactItems>
              <artifactItem>
                <groupId>com.acme</groupId>
                <artifactId>exporter</artifactId>
                <version>${project.version}</version>
                <type>jar</type>
                <outputDirectory>${project.basedir}/src/main/resources</outputDirectory>
                <destFileName>exporter.jar</destFileName>
              </artifactItem>
            </artifactItems>
            <overWriteReleases>false</overWriteReleases>
            <overWriteSnapshots>true</overWriteSnapshots>
          </configuration>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

Note that since we're copying the exporter in the package phase, you will need to run your QA tests
in the `integration-test` phase. To simplify this, we recommend you use the Maven
[failsafe](https://maven.apache.org/surefire/maven-failsafe-plugin/) plugin.

Once done, you have everything you need to write an integration test in the `exporter-qa` module.
For example:

```java
package com.acme.exporter.qa;

import io.zeebe.containers.ZeebeContainer;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@Testcontainers
public final class MyExporterIT {
  @Container
  private final ZeebeContainer zeebe = new ZeebeContainer()
    .withCopyFileToContainer(MountableFile.forClasspathResource("exporter.jar", 0775), "/usr/local/zeebe/exporters/exporter.jar")
    .withEnv("ZEEBE_BROKER_EXPORTERS_ACME_CLASSNAME", "com.acme.exporter.Exporter")
    .withEnv("ZEEBE_BROKER_EXPORTERS_ACME_JARPATH", "/usr/local/zeebe/exporters/exporter.jar")
    .withEnv("ZEEBE_BROKER_EXPORTERS_ACME_ARGS_0_PROP", "foo");

  @Test
  void shouldExportData() {
    // write your test here
  }
}
```

#### Loading Docker image

The idea here is to prepare a Docker image based on
[Zeebe's official image](https://hub.docker.com/r/camunda/zeebe) which already contains your
packaged exporter JAR, so no file mounting or packaging is necessary in advance. Here's an example
of such an image built using [Jib](https://github.com/GoogleContainerTools/jib): [Hazelcast Exporter](https://github.com/camunda-community-hub/zeebe-hazelcast-exporter/blob/bc0d17118150d21bd27d9eead5ef0003680e2f1a/exporter/pom.xml#L111).
However, you do need to re-package your Docker  image after every code change to your exporter
before running your QA test.

Here are some projects which do exactly this:

- [Hazelcast Exporter](https://github.com/camunda-community-hub/zeebe-hazelcast-exporter/blob/main/exporter/src/test/java/io/zeebe/hazelcast/testcontainers/ZeebeTestContainer.java)
- [Redis Exporter](https://github.com/camunda-community-hub/zeebe-redis-exporter/blob/main/exporter/src/test/java/io/zeebe/redis/testcontainers/ZeebeTestContainer.java)

Let's say your build pipeline has already built the image as `ghcr.io/acme/exporter`, and the
exporter JAR is located at `/usr/local/zeebe/exporters/exporter.jar` in that image. Then you could
write the following test:

```java
package com.acme.exporter.qa;

import io.zeebe.containers.ZeebeContainer;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public final class MyExporterIT {
  private static final DockerImageName TEST_IMAGE = DockerImageName.parse("ghcr.io/acme/exporter");

  @Container
  private final ZeebeContainer zeebe = new ZeebeContainer(TEST_IMAGE)
    .withEnv("ZEEBE_BROKER_EXPORTERS_ACME_CLASSNAME", "com.acme.exporter.Exporter")
    .withEnv("ZEEBE_BROKER_EXPORTERS_ACME_JARPATH", "/usr/local/zeebe/exporters/exporter.jar")
    .withEnv("ZEEBE_BROKER_EXPORTERS_ACME_ARGS_0_PROP", "foo");

  @Test
  void shouldExportData() {
    // write your test here
  }
}
```

## Continuous Integration

If you wish to use this with your continous integration pipeline (e.g. Jenkins, CircleCI), the
[Testcontainers](https://www.testcontainers.org/supported_docker_environment/) has a section
explaining how to use it, how volumes can be shared, etc.

# Experimental features

There are currently several experimental features across the project. As described in the
compatibility guarantees, we only guarantee backwards compatibility for stable APIs (marked by the
annotation `@API(status = Status.STABLE)`). Typically, you shouldn't be using anything else.
However, there are some features which already provide value, but for which the correct API is
unclear; these are marked with `@API(status = Status.EXPERIMENTAL)`. These may be changed, or
dropped depending on their usefulness.

> NOTE: you should never use anything marked as `@API(status = Status.INTERNAL)`. These are there
> purely for internal purposes, and cannot be relied on at all.

## Cluster

> NOTE: the cluster API is currently an experimental API. You're encouraged to use it and give
> feedback, as this is how we can validate it. Keep in mind however that it is subject to change in
> the future.

A typical production Zeebe deployment will be a cluster of nodes, some brokers, and possibly some
standalone gateways. It can be useful to test against such deployments for acceptance or E2E tests.

While it's not too hard to manually link several containers, it can become tedious and error-prone
if you want to test many different configurations. The cluster API provides you with an easy way to
programmatically build Zeebe deployments while minimizing the surface of configuration errors.

> NOTE: if you have a static deployment that you don't need to change programmatically per test,
> then you might want to consider setting up a static Zeebe cluster in your CI pipeline (either via
> Helm or docker-compose), or even using Testcontainer's
> [docker-compose](https://www.testcontainers.org/modules/docker_compose/) feature.

### Usage

The main entry point is the `ZeebeClusterBuilder`, which can be instantiated via
`ZeebeCluster#builder()`. The builder can be used to configure the topology of your cluster. You can
configure the following:

- the number of brokers in the cluster (by default 1)
- whether brokers should be using the embedded gateway (by default true)
- the number of standalone gateways in the cluster (by default 0)
- the number of partitions in the cluster (by default 1)
- the replication factor of each partition (by default 1)
- the network the containers should use (by default `Network#SHARED`)

Container instances (e.g. `ZeebeBrokerContainer` or `ZeebeContainer`) are only instantiated and
configured once you call `#build()`. At this point, your cluster is configured in a valid way, but
isn't started yet - that is, no real Docker containers have been created/started.

Once your cluster is built, you can access its brokers and gateways via `ZeebeCluster#getBrokers`
and `ZeebeCluster#getGateways`. This allows you to further configure each container as you wish
before actually starting the cluster.

### Examples

Here is a short example on how to set up a cluster for testing with junit5.

```java
package com.acme.zeebe;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.BrokerInfo;
import io.camunda.client.api.response.Topology;
import io.zeebe.containers.cluster.ZeebeCluster;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Showcases how you can create a test with a cluster of two brokers and one standalone gateway.
 * Configuration is kept to minimum, as the goal here is only to showcase how to connect the
 * different nodes together.
 */
@Testcontainers
class ZeebeClusterWithGatewayExampleTest {

  @Container
  private final ZeebeCluster cluster =
    ZeebeCluster.builder()
      .withEmbeddedGateway(false)
      .withGatewaysCount(1)
      .withBrokersCount(2)
      .withPartitionsCount(2)
      .withReplicationFactor(1)
      .build();

  @Test
  @Timeout(value = 15, unit = TimeUnit.MINUTES)
  void shouldStartCluster() {
    // given
    final Topology topology;

    // when
    try (final CamundaClient client = cluster.newClientBuilder().build()) {
      topology = client.newTopologyRequest().send().join(5, TimeUnit.SECONDS);
    }

    // then
    final List<BrokerInfo> brokers = topology.getBrokers();
    Assertions.assertThat(topology.getClusterSize()).isEqualTo(3);
    Assertions.assertThat(brokers)
      .hasSize(2)
      .extracting(BrokerInfo::getAddress)
      .containsExactlyInAnyOrder(
        cluster.getBrokers().get(0).getInternalCommandAddress(),
        cluster.getBrokers().get(1).getInternalCommandAddress());
  }
}
```

You can find more examples by looking at the
[test/java/io/zeebe/containers/examples/cluster](/core/src/test/java/io/zeebe/containers/examples/cluster)
package.

### Cluster startup time

There are some caveat as well. For example, if you want to create a large cluster with many brokers
and need to increase the
startup time:

```java
package com.acme.zeebe;

import io.zeebe.containers.cluster.ZeebeCluster;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ZeebeHugeClusterTest {

  @Container
  private final ZeebeCluster cluster =
    ZeebeCluster.builder()
      .withEmbeddedGateway(false)
      .withGatewaysCount(3)
      .withBrokersCount(6)
      .withPartitionsCount(6)
      .withReplicationFactor(3)
      // configure each container to have a high start up time as they get started in parallel
      .withNodeConfig(node -> node.self().withStartupTimeout(Duration.ofMinutes(5)))
      .build();

  @Test
  @Timeout(value = 30, unit = TimeUnit.MINUTES)
  void shouldStartCluster() {
    // test things
  }
}
```

## Debugging

There might be cases where you want to debug a container you just started in one of your tests. You
can use the [RemoteDebugger](/core/src/main/java/io/zeebe/containers/util/RemoteDebugger.java)
utility
for this. By default, it will start your container and attach a debugging agent to it on port 5005.
The container startup is then suspended until a debugger attaches to it.

> NOTE: since the startup is suspended until a debugger connects to it, it's possible for the
> startup strategy to time out if no debugger connects to it.

You can use it with any container as:

```java
package com.acme.zeebe;

import io.zeebe.containers.ZeebeContainer;
import io.zeebe.containers.util.RemoteDebugger;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class MyFeatureTest {

  @Container
  private final ZeebeContainer zeebeContainer = RemoteDebugger.configure(new ZeebeContainer());

  @Test
  void shouldTestProperty() {
    // test...
  }
}
```

Note that `RemoteDebugger#configure(GenericContainer<?>)` returns the same container, so you can use
the return value to chain more configuration around your container.

```java
package com.acme.zeebe;

import io.zeebe.containers.ZeebeContainer;
import io.zeebe.containers.util.RemoteDebugger;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class MyFeatureTest {

  @Container
  private final ZeebeContainer zeebeContainer = RemoteDebugger.configure(new ZeebeContainer())
    .withEnv("ZEEBE_BROKER_NETWORK_HOST", "0.0.0.0");

  @Test
  void shouldTestProperty() {
    // test...
  }
}
```

You can also configure the port of the debug server, or even configure the startup to not wait for a
debugger to connect by using `RemoteDebugger#configure(GenericContainer<?>, int, boolean)`. See the
Javadoc for more.

## Volumes and data

Zeebe brokers store all their data under a configurable data directory (default
to `/usr/local/zeebe/data`). By default, when you start a container, this data is ephemeral and will
be deleted when the container is removed.

If you want to keep the data around, there's a few ways you can do so. One option is to use a folder
on the host machine, and mount it as the data directory. Here's an example:

```java
package com.acme.zeebe;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.containers.ZeebeBrokerContainer;
import io.zeebe.containers.ZeebeHostData;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

final class HostDataExampleTest {

  @Test
  @Timeout(value = 5, unit = TimeUnit.MINUTES)
  void shouldSaveDataOnTheHost(@TempDir final Path tempDir) {
    // given
    final ZeebeHostData data = new ZeebeHostData(tempDir.toString());
    try (final ZeebeBrokerContainer container = new ZeebeBrokerContainer().withZeebeData(data)) {
      // when
      container.start();
    }

    // then
    assertThat(tempDir).isNotEmptyDirectory();
  }
}
```

This will have Zeebe write its data directly to a directory on your host machine. Note that the
permissions of the written files will be assigned to the user the container runs in. This means if
the broker process in the container runs as root, then the files written will belong to root, and
your user may not be able to run it. To circumvent this, you will need to run your container as your
user as well. You can do so by modifying the create command passing the correct user to the host
config, e.g. `container.withCreateContainerCmdModifier(cmd -> cmd.withUser("1000:0"))`.

> NOTE: this may or may not work on Windows. I have no access to a Windows machine, so I can't
> really say how permissions should look like for a Windows machine. It's recommended instead to
> use volumes, and extract the data from it if you need.

On the other hand, you can also use a Docker volume for this. The volume is reusable across multiple
runs, and can also be mounted on different containers. Here's an example:

```java
package com.acme.zeebe;

import static org.assertj.core.api.Assertions.assertThatCode;

import io.zeebe.containers.ZeebeBrokerContainer;
import io.zeebe.containers.ZeebeVolume;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

final class VolumeExampleTest {

  @Test
  @Timeout(value = 5, unit = TimeUnit.MINUTES)
  void shouldUseVolume() {
    // given
    final ZeebeVolume volume = ZeebeVolume.newVolume();
    try (final ZeebeContainer container = new ZeebeContainer().withZeebeData(volume)) {
      // when
      container.start();
      container.stop();

      // then
      assertThatCode(() -> container.start()).doesNotThrowExceptions();
    }
  }
}
```

You can see a more complete
example [here](/core/src/test/java/io/zeebe/containers/examples/ReusableVolumeExampleTest.java).

## Extracting data

At times, you may want to extract data from a running container, or from a volume. This can happen
if you want to run a broker locally based on test data for debugging. It can also be used to
generate Zeebe data which can be reused in further test as a starting point to avoid always
regenerating that data.

There are two main interfaces for this. If you want to extract the data from a running container,
you can directly
use [ContainerArchive](/core/src/main/java/io/zeebe/containers/archive/ContainerArchive.java). This
represents a reference to a zipped, TAR file on a given container, which can be extracted to a local
path.

Here's an example which will extract Zeebe's default data directory to `/tmp/zeebe`. This will
result in a copy of the Zeebe data directory at `/tmp/zeebe/usr/local/zeebe/data`.

```java
package com.acme.zeebe;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.containers.ZeebeBrokerContainer;
import io.zeebe.containers.archive.ContainerArchive;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
final class ExtractDataLiveExampleTest {

  @Container
  private final ZeebeBrokerContainer container = new ZeebeBrokerContainer();

  @Test
  void shouldExtractData() {
    // given
    final Path destination = Paths.get("/tmp/zeebe");
    final ContainerArchive archive = ContainerArchive.builder().withContainer(container).build();

    // when
    archive.extract(destination);

    // then
    assertThat(destination).isNotEmptyDirectory();
    assertThat(destination.resolve("usr/local/zeebe/data")).isNotEmptyDirectory();
  }
}
```

> NOTE: if all you wanted was to download the data, you could simply use
> `ContainerArchive#transferTo(Path)`. This will download the zipped archive to the given path as
> is.

You can find more examples for this feature
under [examples/archive](/core/src/test/java/io/zeebe/containers/examples/archive).

## Time traveling

Since version 1.3, Zeebe provides a coarse time traveling API via the actor clock actuator endpoint.
While this actuator is enabled by default, the internal clock is itself immutable. The only thing
you can do out of the box is get the current time as seen by Zeebe's actor framework.

In order to test time based events - such as triggering a timer catch event from one of your
deployed processes - you can start the node with a mutable clock. To do so, you need to set the
configuration flag `zeebe.clock.controlled = true`. This can be done with Testcontainers by passing
the following environment variable:

```java
package com.acme.zeebe;

import io.zeebe.containers.ZeebeNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class SetupMutableClockExample {

  private ZeebeNode<?> node; // make sure this value is instantiated

  @BeforeEach
  void beforeEach() {
    node.withEnv("ZEEBE_CLOCK_CONTROLLED", "true");
  }

  // any subsequent tests can now mutate the clock
  @Test
  void shouldMutateTheClock() {
  }
}
```

> NOTE: if you forget to mutate the clock, the actuator will return a 403 with an explicit error
> message about the clock's immutability.

Once the clock is mutable, you can then use the provided high level API,
[ZeebeClock](/core/src/main/java/io/zeebe/containers/clock/ZeebeClock.java).

Here's a basic example which will simply advance the broker's time:

```java
package io.zeebe.containers.clock;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.containers.ZeebeBrokerContainer;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Execution(ExecutionMode.SAME_THREAD)
@Testcontainers
final class ZeebeClockTest {

  @Container
  private static final ZeebeBrokerContainer BROKER =
    new ZeebeBrokerContainer().withEnv("ZEEBE_CLOCK_CONTROLLED", "true");

  private final ZeebeClock clock = ZeebeClock.newDefaultClock(BROKER);

  @AfterEach
  void afterEach() {
    ZeebeClock.newDefaultClock(BROKER).resetTime();
  }

  @Test
  void shouldAddTime() {
    // given
    final Instant pinnedTime = clock.pinTime(Instant.now());
    final Duration offset = Duration.ofDays(1);

    // when
    final Instant modifiedTime = clock.addTime(offset);

    // then
    final Instant expectedTime = pinnedTime.plus(offset);
    assertThat(modifiedTime).isEqualTo(pinnedTime.plus(offset)).isEqualTo(expectedTime);
  }
}

```

You can find more examples for this feature
under [examples/clock](/core/src/test/java/io/zeebe/containers/examples/clock).

### Tips and limitations

#### Reusing the same container

If you wish to reuse the same container across multiple tests where you modify the time, make sure
to include a trigger after each test (e.g. `@AfterEach` in Junit 5) to reset the clock
(i.e. `ZeebeClock#resetTime()`). It's also recommended you ensure you run all your tests against
that one node sequentially to avoid flaky tests.

#### Embedded gateway issues

Some requests will not play nice with time travel when you modify a broker's clock, if the request
is going through that same broker's embedded gateway. For example, if you create a process instance
and await its result (i.e. `#withResult()`), then advance the clock, the request will time out since
the clock is shared between the broker and the embedded gateway.

## Debug exporter

The module comes with a built-in, injectable debug exporter and receiver pair. This allows you to
stream records out of a broker container into your local tests. You can use this to view the log,
assert certain events are happening, etc. If you're an exporter author, you can use this to verify
that your exporter is exporting the expected events as well.

To use it, you must first start an instance of a `DebugReceiver`, which is a lightweight server
binding to any `InetSocketAddress`. The safest way to construct one is to use the default
constructor, `DebugReceiver(Consumer<Record<?>>)`, which will pick a random, available, local port
to bind to. One receiver per cluster should be used, as receivers can also acknowledge records.

Exported records will be streamed to the given consumer. This is done in a separate thread, thus you
must ensure the consumer is thread-safe. Records are streamed in the order they come in, but will be
sequenced relatively to their partition. This means, all records on partition `P` will be ordered
by increasing position, but ordering between partitions is undefined (though it's roughly time
based, i.e. as the records are exported by the brokers).

Here is the smallest example usage:

```java
package com.acme;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.Record;
import io.zeebe.containers.ZeebeContainer;
import io.zeebe.containers.exporter.DebugReceiver;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

final class WithDebugExporterTest {

  @Test
  void shouldReadExportedRecords() {
    final List<Record<?>> records = new CopyOnWriteArrayList<>();
    try (final DebugReceiver receiver = new DebugReceiver(records::add).start()) {
      final ZeebeContainer container =
        new ZeebeContainer().withDebugExporter(receiver.serverAddress().getPort());

      // ... send some commands that will publish some records.
      // records are then streamed to the consumer, which in our case appends them to the records
      // list.
      assertThat(records).hasSize(1);
    }
  }
}
```

You can view a longer example [here](/core/src/test/java/io/zeebe/containers/examples/exporter).

### Acknowledging records

The receiver can acknowledge records, such that Zeebe will mark them as having been successfully
exported and will not re-export them (and may delete them).

By default, records are _not_ acknowledged, and are never deleted. To acknowledge records, you can
use the `DebugReceiver#acknowledge(int, long)` method, passing the partition ID and the highest
acknowledged position for that partition.

> If you wish records to be auto-acknowledged, you can simply set `Long.MAX_VALUE` as the position
> for each known partition before any records are exported.

There is one limitation, which is that the acknowledged position is the one returned by the server.
This means, if you receive record 1, and then acknowledge that, only when receiving record 2 will
the acknowledged position take effect.

## Zeebe Process Test Compatibility

It's possible to use a container or cluster as the backing engine when working
with [zeebe-process-test](https://github.com/camunda/zeebe-process-test). This will let you reuse
all the assertions you are used to, while running integration tests using one or more actual
Zeebe instances.

### Usage

In order to use it, you will need to add the following dependency to your project:

Maven:

```xml

<dependency>
  <groupId>io.zeebe</groupId>
  <artifactId>zeebe-test-container-engine</artifactId>
  <version>3.5.0</version>
</dependency>
```

Gradle:

```groovy
testImplementation 'io.zeebe:zeebe-test-container-engine:3.5.0'
```

The usage differs a little from normal `zeebe-process-test` usage. Whereas there you would use the
`@ZeebeProcessTest` annotation as your Junit 5 extension, here we stick with the standard
Testcontainers annotations, i.e. the combination of `@Testcontainers` and `@Container`. Doing so
means you keep using familiar tools to manage the lifecycle of your containers, and it lets us be
more flexible when it comes to customizing said containers.

To illustrate, here is a minimal example which deploys a process definition, creates an instance,
and waits for its completion:

```java
package com.acme;

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

@Testcontainers
final class ExampleTest {

  @Container
  private final ContainerEngine engine = ContainerEngine.createDefault();

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
    engine.waitForIdleState(Duration.ofSeconds(5));
    BpmnAssert.assertThat(processInstance).isStarted().isCompleted();
  }
}
```

As you can see, you can keep writing your tests the exact same way as you would with
`zeebe-process-test`, the only difference is in the creation of the `ZeebeTestEngine`.

You can look
at [ContainerEngine](/engine/src/main/java/io/zeebe/containers/engine/ContainerEngine.java) for an
explanation of how it differs from the normal
`ZeebeTestEngine`, and for hints on how to build customized engines. There are also
some [examples](/engine/src/test/java/io/zeebe/containers/engine/examples) which hopefully
illustrate how to use this with all the customization options.

### Reference

The integration is done by leveraging a few other experimental features. When you create a container
engine, the underlying containers are automatically configured to have a
[controllable clock](#time-traveling) and to export their records using the
[debug exporter](#debug-exporter).

**This means when configuring the containers manually, do not disable or override these two
features.**

### Caveats

#### Idle/busy state

The bundled, stripped down engine available with `zeebe-process-test` allows for much tighter
integration with the assertions, such that it can accurately report if it is idle or busy. When
running with a real Zeebe instance, this is hidden from us.

Instead, we define idle to mean that no records are exported by the debug exporter for a certain
period of time (the `ContainerEngineBuilder#withIdlePeriod` configuration). The idle state is
defined over all partitions, not only a single partition.

Similarly, we define a busy state as at least one record was exported during the timeout period.

### Awaitility issues

If you wish to wrap your `BpmnAssert` calls with `Awaitility` for resilience, note that `BpmnAssert`
sets the record stream source as a thread local. Since `Awaitility`, by default, polls on different
threads, you may run into issues where no record stream source is found, or the wrong record stream
source is used.

To properly use it, make sure to either configure `Awaility#pollInSameThread`, or in your callback,
overwrite the thread local stream source with `BpmnAssert#initRecordStreamSource`. This latter is
not recommended if you run your tests in parallel, however, as you may affect other tests.

# Tips

## Tailing your container's logs during development

As containers are somewhat opaque by nature (though Testcontainers already does a great job of
making this more seamless), it's very useful to add a `LogConsumer` to a container. This will
consume your container logs and pipe them out to your consumer. If you're using SLF4J, you can use
the `Slf4jLogConsumer` and give it a logger, which makes it much easier to debug if anything goes
wrong. Here's an example of how this would look like:

```java
package com.acme.zeebe;

import io.zeebe.containers.ZeebeContainer;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class MyFeatureTest {

  private static final Logger LOGGER =
    LoggerFactory.getLogger("com.acme.zeebe.MyFeatureTest.zeebeContainer");

  @Container
  private final ZeebeContainer zeebeContainer =
    new ZeebeContainer().withLogConsumer(new Slf4jLogConsumer(LOGGER));

  @Test
  void shouldTestProperty() {
    // test...
  }
}
```

## Configuring GenericContainer specific properties with a Zeebe*Node interface

There are three container types in this module: `ZeebeContainer`, `ZeebeBrokerContainer`,
`ZeebeGatewayContainer`. `ZeebeContainer` is a special case which can be both a gateway and a
broker. To support a more generic handling of broker/gateway concept, there are two types introduced
which are `ZeebeBrokerNode` and `ZeebeGatewayNode`. These types, however, are not directly extending
`GenericContainer`.

At times, when you have a `ZeebeBrokerNode` or `ZeebeGatewayNode`, you may want to additionally
configure it in ways that are only available to `GenericContainer` instances. You can use
`Container#self()` to get the node's `GenericContainer` representation.

For example:

```java
package com.acme.zeebe;

import io.zeebe.containers.cluster.ZeebeCluster;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class ZeebeHugeClusterTest {

  private final ZeebeCluster cluster =
    ZeebeCluster.builder()
      .withBrokersCount(1)
      .build();

  @AfterEach
  void tearDown() {
    cluster.stop();
  }

  @Test
  @Timeout(value = 5, unit = TimeUnit.MINUTES)
  void shouldStartCluster() {
    // given
    final ZeebeBrokerNode<?> broker = cluster.getBrokers().get(0);
    broker.self().withStartupTimeout(Duration.ofMinutes(2));
    cluster.start();

    // test more things
  }
}
```

## Advanced Docker usage via the DockerClient

Remember that you have access to the raw docker client via `GenericContainer#getDockerClient()`.
This lets you do all kinds of things at runtime, such as fiddling with volumes, networks, etc.,
which is a great way to test failure injection.

### Gracefully restarting a container

> NOTE: this is an advanced feature which is experimental and may still cause issues, so use at your
> own risk.

`Testcontainers` itself does not provide a convenience method to stop or pause a container. Using
`GenericContainer#stop()` will actually kill and remove the container. To do these things, you can
use the `DockerClient` directly, and send standard docker commands (e.g. stop, pause, etc.).

Alternatively, we provide a convenience method as `ZeebeNode#shutdownGracefully()`
(e.g. `ZeebeContainer#shutdownGracefully()`, `ZeebeBrokerContainer#shutdownGracefully()`). This will
only stop the container with a grace period, after which it will kill the container if not stopped.
However, the container is not removed, and can be restarted later.

Keep in mind that to restart it you need to use a `DockerClient#startContainerCmd(String)`, as just
calling `GenericContainer#start()` will not start your container again.

## Limiting container resources

When starting many containers, you can use `GenericContainer#withCreateContainerCmdModifier()` on
creation to limit the resources available to them. This can be useful when testing locally on a
development machine and having to start multiple containers.

# Contributing

Contributions are more than welcome! Please make sure to read and adhere to the
[Code of Conduct](CODE_OF_CONDUCT.md). Additionally, in order to have your contributions accepted,
you will need to sign the [Contributor License Agreement](https://cla-assistant.io/camunda/).

## Build from source

### Prerequisites

In order to build from source, you will need to install maven 3.6+. You can find more about it on
the [maven homepage](https://maven.apache.org/users/index.html).

To build the project, you will need a JDK 21 installed locally. Note however that the `core` and `engine` modules
are targeting Java 8 for compatibility purposes, and we must ensure that we maintain compatibility.
To do this, the CI pipeline will run the tests using Java 8.

Finally, you will need to [install Docker](https://docs.docker.com/get-docker/) on your local
machine.

### Modules

The library is split into three modules:

- `core`: the core library. It's artifact ID is `zeebe-test-container` for backwards compatibility.
  This is what users will include in their project.
- `engine`: the implementation of `ZeebeTestEngine`, the compatibility layer between this library
  and [Zeebe Process Test](https://github.com/camunda/zeebe-process-test).
- `exporter`: the debug exporter module. It will be packaged as a fat JAR and included as a resource
  in the core module. It has to be a separate module as it targets Java 21, same as the
  `zeebe-exporter-api` module it implements.
- `exporter-test`: a module to test the integration between `DebugReceiver` and `DebugExporter`,
  without having to run everything through an actual broker.

### Building

With all requirements ready, you can now
simply [clone the repository](https://docs.github.com/en/github/creating-cloning-and-archiving-repositories/cloning-a-repository)
, and from its root, run the following command:

```shell
mvn clean install
```

This will build the project and run all tests locally.

Should you wish to only build without running the tests, you can run:

```shell
mvn clean package
```

## Testing

Testing is done via GitHub actions, using two workflows:

- [lint.yml](/.github/workflows/lint.yml): includes jobs which ensure code quality (e.g. static
  analysis, linting, formatting, etc.)
- [test.yml](/.github/workflows/test.yml): testing jobs for each module. For the `core` module,
  there are two testing jobs, one using TestContainers Cloud, and one using the local Docker daemon.
  This is due to a limitation of TestContainers Cloud, which does not work with host binds. Any
  tests
  which need to run on the local job should be annotated with `@DisabledIfTestcontainersCloud`.

One important thing to note is that we package and copy the debug exporter into the core module
during the build process. This means that any tests which relies on the debug exporter being
accessible has to be an integration test run by failsafe (i.e. test files ending with `IT`).

## Code style

The project uses Spotless to apply consistent formatting and licensing to all project files. By
default, the build only performs the required checks. If you wish to auto format/license your code,
run:

```shell
mvn spotless:apply
```

## Backwards compatibility

Zeebe Test Container uses a [Semantic Versioning](https://semver.org/) scheme for its versions, and
[revapi](https://revapi.org/) to enforce backwards compatibility according to its specification.

Additionally, we also use [apiguardian](https://github.com/apiguardian-team/apiguardian) to specify
backwards compatibility guarantees on a more granular level. As such, only APIs marked as `STABLE`
are considered when enforcing backwards compatibility.

If you wish to incubate a new feature, or if you're unsure about a new API type/method, please use
the `EXPERIMENTAL` status for it. This will give us flexibility to test out new features and change
them easily if we realize they need to be adapted.

Note that this only applies to the `core/` module. All other modules are currently considered
implementation details, and are not released anyway.

## Report issues or contact developers

Work on Zeebe Test Container is done entirely through the GitHub repository. If you want to report a
bug or request a new feature feel free to open a new issue
on [GitHub issues](https://github.com/camunda-community-hub/zeebe-test-container/issues).

## Create a Pull Request

To work on an issue, follow the following steps:

1. Ensure that an [issue](https://github.com/camunda-community-hub/zeebe-test-container/issues)
   exists for the task you want to work on.
   If one does not, create one.
2. Checkout the `master` branch and pull the latest changes.

   ```
   git checkout develop
   git pull
   ```
3. Create a new branch with the naming scheme `issueId-description`.

   ```
   git checkout -b 123-my-new-feature
   ```
4. Follow
   the [Google Java Format](https://github.com/google/google-java-format#intellij-android-studio-and-other-jetbrains-ides)
   and [Zeebe Code Style](https://github.com/zeebe-io/zeebe/wiki/Code-Style) while coding.
5. Implement the required changes on your branch, and make sure to build and test your changes
   locally before opening a pull requests for review.
6. If you want to make use of the CI facilities before your feature is ready for review, feel free
   to open a draft PR.
7. If you think you finished the issue please prepare the branch for reviewing. In general the
   commits should be squashed into meaningful commits with a helpful message. This means cleanup/fix
   etc. commits should be squashed into the related commit.
8. Finally, be sure to check on the CI results and fix any reported errors.

## Commit Message Guidelines

Commit messages use [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/#summary)
format, with a slight twist. See the
[Zeebe commit guidelines for more](https://github.com/camunda-cloud/zeebe/blob/develop/CONTRIBUTING.md#commit-message-guidelines)
.

## Contributor License Agreement

You will be asked to sign our Contributor License Agreement when you open a Pull Request. We are not
asking you to assign copyright to us, but to give us the right to distribute your code without
restriction. We ask this of all contributors in order to assure our users of the origin and
continuing existence of the code. You only need to sign the CLA once.

Note that this is a general requirement of any Camunda Community Hub project.
