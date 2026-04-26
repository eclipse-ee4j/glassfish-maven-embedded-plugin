# Maven Embedded GlassFish Plugin

A Maven plugin for managing Embedded GlassFish server instances during the build lifecycle.

Supports Eclipse GlassFish 6, 7, 8, or newer.

## Quick Start

Run your project's main artifact on Embedded GlassFish directly from command line without modifying your `pom.xml`:

```bash
mvn org.glassfish.embedded:embedded-glassfish-maven-plugin:8.0:run -Dglassfish.version=8.0.1
```

Or add the plugin to your `pom.xml`:

```xml
<plugin>
    <groupId>org.glassfish.embedded</groupId>
    <artifactId>embedded-glassfish-maven-plugin</artifactId>
    <version>8.0</version>
    <configuration>
        <glassfish.version>8.0.1</glassfish.version>
    </configuration>
</plugin>
```

Start the server with your application and wait until it stops:
```bash
mvn embedded-glassfish:run
```

## Prerequisites

- JDK 11 or higher
- Maven 3.6.3 or higher

## Basic Usage

### Command Line

Start server in background:
```bash
mvn embedded-glassfish:start
```

Deploy application:
```bash
mvn embedded-glassfish:deploy
```

Stop server:
```bash
mvn embedded-glassfish:stop
```

### Integration Testing

```xml
<plugin>
    <groupId>org.glassfish.embedded</groupId>
    <artifactId>embedded-glassfish-maven-plugin</artifactId>
    <version>7.0</version>
    <executions>
        <execution>
            <id>start-server</id>
            <phase>pre-integration-test</phase>
            <goals>
                <goal>start</goal>
                <goal>deploy</goal>
            </goals>
        </execution>
        <execution>
            <id>stop-server</id>
            <phase>post-integration-test</phase>
            <goals>
                <goal>undeploy</goal>
                <goal>stop</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

## Goals Overview

| Goal | Description | Default Phase |
|------|-------------|---------------|
| [`run`](#run) | Starts server, deploys apps, and runs interactively | none |
| [`start`](#start) | Starts an Embedded GlassFish server | pre-integration-test |
| [`stop`](#stop) | Stops the Embedded GlassFish server | post-integration-test |
| [`deploy`](#deploy) | Deploys an application to the server | pre-integration-test |
| [`undeploy`](#undeploy) | Undeploys an application from the server | post-integration-test |
| [`admin`](#admin) | Executes admin commands | pre-integration-test |

## Configuration

### Basic Configuration

```xml
<plugin>
    <groupId>org.glassfish.embedded</groupId>
    <artifactId>embedded-glassfish-maven-plugin</artifactId>
    <version>7.0</version>
    <configuration>
        <port>8080</port>
        <app>${project.build.directory}/${project.build.finalName}.war</app>
    </configuration>
</plugin>
```

- `port` - HTTP port number for the server (default: 8080)
- `app` - Path to the application artifact to deploy (default: main artifact)

### Automatic Artifact Deployment

The plugin automatically detects and deploys your project's main artifact without requiring explicit configuration:

```xml
<plugin>
    <groupId>org.glassfish.embedded</groupId>
    <artifactId>embedded-glassfish-maven-plugin</artifactId>
    <version>7.0</version>
    <configuration>
        <!-- Main artifact automatically deployed if no <app> parameter specified -->
    </configuration>
</plugin>
```

**Note:** Currently assumes WAR packaging. For other artifact types, specify the `app` parameter explicitly.

### Admin Commands

Execute administrative commands on the running server:

```xml
<plugin>
    <groupId>org.glassfish.embedded</groupId>
    <artifactId>embedded-glassfish-maven-plugin</artifactId>
    <version>7.0</version>
    <configuration>
        <commands>
            <command>set configs.config.server-config.network-config.protocols.protocol.http-listener.http.websockets-support-enabled=true</command>
            <command>create-jdbc-resource --connectionpoolid mypool jdbc/myresource</command>
        </commands>
    </configuration>
</plugin>
```

Command line usage:
```bash
mvn embedded-glassfish:admin -Dcommands="set server.monitoring-service.module-monitoring-levels.web-container=HIGH"
```

## Configuration Reference

### Server Configuration
- `configFile` - Custom domain configuration file
- `glassfish.version` - GlassFish version to use (default: 8.0.0)
- `port` - HTTP port number (default: 8080)
- `ports` - Map of port configurations
- `serverID` - Server identifier (default: "maven")

### Application Deployment
- `app` - Path to application artifact to deploy (defaults to `${project.build.directory}/${project.build.finalName}.war`)
- `contextRoot` - Application context root
- `name` - Application name (default: "myapp")

### Advanced Configuration
- `bootstrapProperties` - Bootstrap properties
- `glassfishProperties` - GlassFish server properties
- `instanceRoot` - Server instance root directory

## Goal Reference

### run
Starts the server, deploys the project's WAR artifact by default, and waits for user input.
Press Enter to redeploy, type `X` to undeploy and exit.

A different artifact can be specified via the `app` configuration parameter.

**Default Phase:** none (manual execution)

**Example:**
```bash
mvn embedded-glassfish:run
```

```xml
<!-- Deploy a specific artifact instead of the default WAR -->
<configuration>
    <app>${project.build.directory}/myapp.war</app>
</configuration>
```

### start
Starts an Embedded GlassFish server with the configured parameters.

**Default Phase:** pre-integration-test

**Example:**
```bash
mvn embedded-glassfish:start
```

### stop
Stops the Embedded GlassFish server and cleans up resources.

**Default Phase:** post-integration-test

**Example:**
```bash
mvn embedded-glassfish:stop
```

### deploy
Deploys an application to the running Embedded GlassFish server.

**Default Phase:** pre-integration-test

**Configuration:**
- `app` - Application path (auto-detects if not specified)
- `name` - Application name (default: "myapp")
- `contextRoot` - Web application context root
- `deploymentParams` - Raw `asadmin deploy` parameters, for options not covered by the above (e.g. `--precompilejsp=true`, `--createtables=true`)

```xml
<configuration>
    <deploymentParams>
        <param>--contextroot=greetings</param>
        <param>--name=myapp</param>
        <param>--precompilejsp=true</param>
    </deploymentParams>
</configuration>
```

**Example:**
```bash
mvn embedded-glassfish:deploy -Dapp=target/myapp.war
```

### undeploy
Undeploys an application from the Embedded GlassFish server.

**Default Phase:** post-integration-test

**Configuration:**
- `name` - Application name to undeploy (default: "myapp")
- `undeploymentParams` - Additional undeployment parameters

**Example:**
```bash
mvn embedded-glassfish:undeploy -Dname=myapp
```

### admin
Executes administrative commands on the running server.

**Default Phase:** pre-integration-test

**Configuration:**
- `commands` - Array of admin commands to execute

**Example:**
```bash
mvn embedded-glassfish:admin -Dcommands="create-jdbc-resource --connectionpoolid mypool jdbc/myresource"
```

## Advanced Usage

### Forked JVM Mode

By default, the `start` and `run` goals launch GlassFish in a **forked JVM** — a separate process isolated from the Maven JVM. This avoids classloader conflicts and JVM option interference between Maven and GlassFish.

When `start` forks GlassFish, subsequent goals (`deploy`, `undeploy`, `admin`, `stop`) automatically detect the forked process and communicate with it — no extra configuration needed.

To run GlassFish in-process instead:

```bash
mvn embedded-glassfish:start -Dglassfish.start.fork=false
mvn embedded-glassfish:run   -Dglassfish.run.fork=false
```

Or in `pom.xml`:
```xml
<configuration>
    <fork>false</fork>
</configuration>
```

### Non-interactive run mode

The `run` goal normally waits for user input. Set `stop=true` to skip the interactive loop — GlassFish starts, deploys all apps, then immediately undeploys and stops. Useful for automated integration tests:

```xml
<execution>
    <id>integration</id>
    <phase>integration-test</phase>
    <goals>
        <goal>run</goal>
    </goals>
    <configuration>
        <stop>true</stop>
    </configuration>
</execution>
```

Or via command line:
```bash
mvn embedded-glassfish:run -Dglassfish.run.stop=true
```

## License

Eclipse Public License v. 2.0
