/*
 * Copyright (c) 2023, 2026 Contributors to the Eclipse Foundation.
 * Copyright (c) 2010, 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */
package org.glassfish.maven;

import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.maven.artifact.Artifact;

/**
 * This Mojo starts Embedded GlassFish, executes all the 'admin' goals, and executes all 'deploy' goals, and waits for
 * user's input.
 * <p/>
 * <p/>
 * While it is waiting for user's input, the user can access the deployed applications.
 * <p/>
 * Upon user's input, it undeploys all the applications that were defined in all 'deploy' goals, and redeploys all of
 * them.
 * <p/>
 * If user enters 'X' in their console for this Mojo will stop Embedded GlassFish and will exit.
 *
 * @author bhavanishankar@dev.java.net
 */
@Mojo(name = "run", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST)
public class RunMojo extends AbstractDeployMojo {

    /**
     * Abstracts the GlassFish operations so the deploy loop can be shared
     * between in-process and forked execution modes.
     */
    private interface GlassFishCommands {
        void runAdminCommand(String commandLine) throws Exception;
        void deploy(String archivePath, String[] params) throws Exception;
        void undeploy(String appName) throws Exception;
        void stop() throws Exception;
    }

    /**
     * When true, GlassFish will be stopped after all admin commands and deployments have been executed,
     * instead of waiting for user input. Can also be set via the Maven property {@code glassfish.run.stop}.
     * Mainly intended for automated tests.
     */
    @Parameter(property = "glassfish.run.stop", defaultValue = "false")
    private boolean stop;

    /**
     * When true, GlassFish is started in a forked JVM. Communication with the forked process happens
     * via stdin/stdout. Can also be set via the Maven property {@code glassfish.run.fork}.
     */
    @Parameter(property = "glassfish.run.fork", defaultValue = "true")
    private boolean fork;

    public void execute() throws MojoExecutionException, MojoFailureException {
        if (fork) {
            executeForked();
        } else {
            executeInProcess();
        }
    }

    private void executeForked() throws MojoExecutionException {
        try {
            Properties bootstrapProps = getBootStrapProperties();
            Properties glassfishProps = getGlassFishProperties();

            File configFile = writeForkedConfig(bootstrapProps, glassfishProps);

            File gfJar = getGlassFishJar();
            File pluginJar = getPluginJar();
            String classpath = pluginJar.getAbsolutePath() + File.pathSeparator + gfJar.getAbsolutePath();

            String javaExecutable = ProcessHandle.current().info().command()
                    .orElseGet(() -> System.getProperty("java.home") + File.separator + "bin"
                            + File.separator + "java");

            ProcessBuilder glassFishProcessBuilder = new ProcessBuilder(
                    javaExecutable,
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "--add-opens=java.base/java.util=ALL-UNNAMED",
                    "--add-opens=java.base/sun.nio.fs=ALL-UNNAMED",
                    "--add-opens=java.base/sun.net.www.protocol.jrt=ALL-UNNAMED",
                    "--add-opens=java.naming/javax.naming.spi=ALL-UNNAMED",
                    "--add-opens=java.rmi/sun.rmi.transport=ALL-UNNAMED",
                    "--add-opens=jdk.management/com.sun.management.internal=ALL-UNNAMED",
                    "--add-exports=java.naming/com.sun.jndi.ldap=ALL-UNNAMED",
                    "--add-exports=java.base/jdk.internal.vm.annotation=ALL-UNNAMED",
                    "--add-opens=java.base/jdk.internal.vm.annotation=ALL-UNNAMED",
                    "--add-exports=java.base/jdk.internal.loader=ALL-UNNAMED",
                    "-cp", classpath,
                    GlassFishForkedRunner.class.getName(),
                    configFile.getAbsolutePath()
            );
            glassFishProcessBuilder.redirectErrorStream(true);
            Process process = glassFishProcessBuilder.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            }, "glassfish-process-cleanup"));

            BufferedReader childOut = new BufferedReader(new InputStreamReader(process.getInputStream()));

            // Wait for READY signal, printing all lines until then
            String line;
            while ((line = childOut.readLine()) != null) {
                System.out.println(line);
                System.out.flush();
                if (GlassFishForkedRunner.RESP_READY.equals(line.trim())) {
                    break;
                }
            }
            if (!process.isAlive()) {
                throw new MojoExecutionException("Forked GlassFish process ended before sending READY");
            }

            // Continue pumping remaining output in background
            Thread pumpThread = new Thread(() -> {
                try {
                    String pumpLine;
                    while ((pumpLine = childOut.readLine()) != null) {
                        System.out.println(pumpLine);
                        System.out.flush();
                    }
                } catch (Exception ignored) {
                }
            }, "glassfish-stdout-pump");
            pumpThread.setDaemon(true);
            pumpThread.start();

            BufferedWriter childIn = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

            GlassFishCommands commands = new GlassFishCommands() {
                public void runAdminCommand(String commandLine) throws Exception {
                    sendCommand(childIn, GlassFishForkedRunner.CMD_ADMIN + " " + commandLine);
                }
                public void deploy(String archivePath, String[] params) throws Exception {
                    String paramStr = params.length > 0 ? " " + String.join(" ", params) : "";
                    sendCommand(childIn, GlassFishForkedRunner.CMD_DEPLOY + " " + archivePath + paramStr);
                }
                public void undeploy(String appName) throws Exception {
                    sendCommand(childIn, GlassFishForkedRunner.CMD_UNDEPLOY + " " + appName);
                }
                public void stop() throws Exception {
                    sendCommand(childIn, GlassFishForkedRunner.CMD_STOP);
                }
            };

            runDeployLoop(commands);
            process.waitFor();
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private void sendCommand(BufferedWriter childIn, String command) throws Exception {
        childIn.write(command);
        childIn.newLine();
        childIn.flush();
    }

    /**
     * Shared deploy/undeploy/redeploy loop used by both in-process and forked execution modes.
     * Runs admin commands, then repeatedly deploys, optionally waits for user input to redeploy,
     * and finally stops GlassFish.
     */
    private void runDeployLoop(GlassFishCommands gf) throws Exception {
        for (Properties command : getAdminCommandConfigurations()) {
            for (String cmd : (List<String>) command.get("commands")) {
                gf.runAdminCommand(cmd);
            }
        }

        while (true) {
            List<Properties> deployments = getDeploymentConfigurations();

            for (Properties deployment : deployments) {
                gf.deploy(getApp(deployment.getProperty("app")), getDeploymentParameters(deployment));
            }

            if (stop) {
                for (Properties deployment : deployments) {
                    gf.undeploy(deployment.getProperty("name"));
                }
                break;
            }

            System.out.println("Hit ENTER to redeploy, X to exit");
            String input = new BufferedReader(new InputStreamReader(System.in)).readLine();

            for (Properties deployment : deployments) {
                gf.undeploy(deployment.getProperty("name"));
            }

            if (input.equalsIgnoreCase("X")) {
                break;
            }
        }

        gf.stop();
    }

    private void executeInProcess() throws MojoExecutionException, MojoFailureException {
        try {
            startGlassFish(serverID, getClassLoader(), getBootStrapProperties(),
                    getGlassFishProperties());

            GlassFishCommands commands = new GlassFishCommands() {
                public void runAdminCommand(String commandLine) throws Exception {
                    runCommand(serverID, getClassLoader(), new String[]{commandLine});
                }
                public void deploy(String archivePath, String[] params) throws Exception {
                    doDeploy(serverID, getClassLoader(), getBootStrapProperties(),
                            getGlassFishProperties(), new File(archivePath), params);
                }
                public void undeploy(String appName) throws Exception {
                    doUndeploy(serverID, getClassLoader(), getBootStrapProperties(),
                            getGlassFishProperties(), appName, new String[0]);
                }
                public void stop() throws Exception {
                    stopGlassFish(serverID, getClassLoader());
                }
            };

            runDeployLoop(commands);
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    // Retrieve all the "admin" goals defined in the plugin.
    private List<Properties> getAdminCommandConfigurations() {

        List<Properties> deployments = new ArrayList<Properties>();

        Plugin embeddedPlugin = getPlugin("embedded-glassfish-maven-plugin");

        List<PluginExecution> deployGoals = getGoals(embeddedPlugin, "admin");

        for (PluginExecution pluginExecution : deployGoals) {
            Properties configurations = getConfigurations(
                    pluginExecution, embeddedPlugin, "commands");
            deployments.add(configurations);
        }

        return deployments;
    }

    // Retrieve all the "deploy" goals defined in the plugin.
    private List<Properties> getDeploymentConfigurations() {

        List<Properties> deployments = new ArrayList<>();

        Plugin embeddedPlugin = getPlugin("embedded-glassfish-maven-plugin");

        List<PluginExecution> deployGoals = getGoals(embeddedPlugin, "deploy");

        for (PluginExecution pluginExecution : deployGoals) {
            Properties configurations = getConfigurations(
                    pluginExecution, embeddedPlugin, "deploymentParams");
            deployments.add(configurations);
        }

        /* If no deploy goal specified, add a default deployment */
        if (deployGoals.isEmpty()) {
            Properties configurations = getConfigurations(
                    null, embeddedPlugin, "deploymentParams");
            deployments.add(configurations);
        }

        return deployments;
    }

    /**
     * From the maven project retrieve the plugin by given name.
     *
     * @param name Name of the plugin
     * @return Plugin by given name defined in the maven project
     */
    private Plugin getPlugin(String name) {
        List plugins = project.getModel().getBuild().getPlugins();
        for (Object plugin : plugins) {
            if (((Plugin) plugin).getArtifactId().equals(name)) {
                return (Plugin) plugin;
            }
        }
        return null;
    }

    /**
     * Get all the goals by given name in the plugin.
     *
     * @param plugin Plugin to which the goal belongs.
     * @param goalName Name of the goals to be retrieved from the plugin
     * @return List of goals by given name in the given plugin
     */
    private List<PluginExecution> getGoals(Plugin plugin, String goalName) {
        List executions = plugin.getExecutions();
        List<PluginExecution> goals = new ArrayList<>();
        for (Object execution : executions) {
            PluginExecution pe = (PluginExecution) execution;
            List allGoals = pe.getGoals();
            for (Object goal : allGoals) {
                if (((String) goal).equals(goalName)) {
                    goals.add(pe);
                }
            }
        }
        return goals;
    }

    private Properties getConfigurations(PluginExecution goal, Plugin plugin,
            String... nonLeafNodeNames) {
        Xpp3Dom config = (Xpp3Dom) plugin.getConfiguration();

        Properties configurations = new Properties();

        // retrieve configuration options at the plugin level.
        if (config != null) {
            configurations.putAll(getConfigurations(config));
            if (nonLeafNodeNames != null) {
                for (String nonLeafNodeName : nonLeafNodeNames) {
                    configurations.put(nonLeafNodeName, getConfigurationsAsList(
                            config.getChild(nonLeafNodeName)));
                }
            }
        }

        // retrieve configuration options at goal level.
        if (goal != null) {
            config = (Xpp3Dom) goal.getConfiguration();
            if (config != null) {
                configurations.putAll(getConfigurations(config));
                if (nonLeafNodeNames != null) {
                    for (String nonLeafNodeName : nonLeafNodeNames) {
                        List<String> value = (List<String>) configurations.get(nonLeafNodeName);
                        value.addAll(getConfigurationsAsList(config.getChild(nonLeafNodeName)));
                        configurations.put(nonLeafNodeName, value);
                    }
                }
            }
        }

        return configurations;
    }

    /**
     * Get all the leaf level i.e., string property-values in the given node.
     *
     * @param node node from which the property-values to be retrieved.
     * @return all the leaf level i.e., string property-values of the given node.
     */
    private Properties getConfigurations(Xpp3Dom node) {
        Properties properties = new Properties();
        if (node != null) {
            Xpp3Dom[] configs = node.getChildren();
            if (configs != null) {
                for (Xpp3Dom config : configs) {
                    if (config.getValue() != null) { // The child is at the leaf level, so it has string prop-value.
                        properties.setProperty(config.getName(), config.getValue());
                    }
                }
            }
        }
        return properties;
    }

    /**
     * Read the configurations that are like this:
     * <p/>
     * <configurations>
     * <configuration>a=b</configuration>
     * <configuration>x=y</configuration>
     * </configurations>
     *
     * @param node Base node from where the configurations should be read.
     * @return List of configurations.
     */
    private List<String> getConfigurationsAsList(Xpp3Dom node) {
        List<String> configurations = new ArrayList<>();
        if (node != null) {
            Xpp3Dom[] configs = node.getChildren();
            if (configs != null) {
                for (Xpp3Dom config : configs) {
                    if (config.getValue() != null) { // The child is at the leaf level, so it has string prop-value.
                        configurations.add(config.getValue());
                    }
                }
            }
        }
        return configurations;
    }

    public void runCommand(String serverId, ClassLoader cl,
            String[] commandLines) throws Exception {
        Class clazz = cl.loadClass(PluginUtil.class.getName());
        Method m = clazz.getMethod("runCommand", new Class[]{
            String.class, String[].class});
        m.invoke(null, new Object[]{serverId, commandLines});
    }

}
