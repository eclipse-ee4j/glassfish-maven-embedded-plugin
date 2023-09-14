/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation.
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
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

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

    public void execute() throws MojoExecutionException, MojoFailureException {

        List<Properties> commands = getAdminCommandConfigurations();
        try {
            /**
             * Start GlassFish server.
             */
            startGlassFish(serverID, getClassLoader(), getBootStrapProperties(),
                    getGlassFishProperties());

            /**
             * Execute all 'admin' goals.
             */
            for (Properties command : commands) {
                runCommand(serverID, getClassLoader(),
                        ((List<String>) command.get("commands")).toArray(new String[0]));
            }

            while (true) {
                List<Properties> deployments = getDeploymentConfigurations();

                /**
                 * Execute all 'deploy' goals.
                 */
                for (Properties deployment : deployments) {
                    doDeploy(serverID, getClassLoader(), getBootStrapProperties(),
                            getGlassFishProperties(), new File(getApp(deployment.getProperty("app"))),
                            getDeploymentParameters(deployment));
                }

                /**
                 * Wait for user input.
                 */
                System.out.println("Hit ENTER to redeploy, X to exit");
                String str = new BufferedReader(new InputStreamReader(System.in)).readLine();

                /**
                 * Undeploy all the applications deployed via 'deploy' goal.
                 */
                for (Properties deployment : deployments) {
                    doUndeploy(serverID, getClassLoader(), getBootStrapProperties(),
                            getGlassFishProperties(), deployment.getProperty("name"), new String[0]);

                }
                /**
                 * Exit from embedded-glassfish:run if 'X' is entered.
                 */
                if (str.equalsIgnoreCase("X")) {
                    break;
                }
            }
            /**
             * Stop GlassFish server
             */
            stopGlassFish(serverID, getClassLoader());
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
