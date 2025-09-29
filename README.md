# Maven Embedded GlassFish Plugin

A Maven plugin for managing Embedded GlassFish server instances during the build lifecycle.

## Quick Start

Run your project's main artifact on Embedded GlassFish directly from command line without modifying your `pom.xml`:

```bash
mvn org.glassfish.embedded:embedded-glassfish-maven-plugin:7.1-SNAPSHOT:run -DglassfishVersion=7.0.25
```

Or add the plugin to your `pom.xml`:

```xml
<plugin>
    <groupId>org.glassfish.embedded</groupId>
    <artifactId>embedded-glassfish-maven-plugin</artifactId>
    <version>7.1-SNAPSHOT</version>
    <configuration>
        <glassfishVersion>7.0.25</glassfishVersion>
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
    <version>7.1-SNAPSHOT</version>
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
| [`start`](#start) | Starts an Embedded GlassFish server | pre-integration-test |
| [`stop`](#stop) | Stops the Embedded GlassFish server | post-integration-test |
| [`deploy`](#deploy) | Deploys an application to the server | pre-integration-test |
| [`undeploy`](#undeploy) | Undeploys an application from the server | post-integration-test |
| [`run`](#run) | Starts server and keeps it running | none |
| [`admin`](#admin) | Executes admin commands | none |

## Configuration

### Basic Configuration

```xml
<plugin>
    <groupId>org.glassfish.embedded</groupId>
    <artifactId>embedded-glassfish-maven-plugin</artifactId>
    <version>7.1-SNAPSHOT</version>
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
    <version>7.1-SNAPSHOT</version>
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
    <version>7.1-SNAPSHOT</version>
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
- `glassfishVersion` - GlassFish version to use
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

### start
Starts an Embedded GlassFish server with the configured parameters.

**Default Phase:** pre-integration-test

**Configuration:**
- Uses all server configuration parameters (serverID, port, glassfishProperties, etc.)

**Example:**
```bash
mvn embedded-glassfish:start
```

### stop
Stops the Embedded GlassFish server and cleans up resources.

**Default Phase:** post-integration-test

**Configuration:**
- `serverID` - Server identifier to stop (default: "maven")

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
- `deploymentParams` - Additional deployment parameters

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

### run
Starts the server, deploys applications, and runs interactively. Allows redeployment by pressing Enter or exit by typing 'X'.

**Default Phase:** none (manual execution)

**Configuration:**
- Combines all server and deployment configurations
- Executes all configured admin and deploy goals

**Example:**
```bash
mvn embedded-glassfish:run
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

## License

Eclipse Public License v. 2.0
