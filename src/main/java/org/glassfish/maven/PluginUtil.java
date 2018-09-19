/*
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

import org.glassfish.embeddable.*;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author bhavanishankar@dev.java.net
 */
public class PluginUtil {

    private static final Logger logger = Logger.getLogger("maven-embedded-glassfish-plugin");

    static {
        logger.setLevel(Level.FINE);
    }

    static GlassFishRuntime gfr;
    // Map with Key=serverId Value=GlassFish
    private final static Map<String, GlassFish> gfMap =
            new HashMap<String, GlassFish>();

    public static GlassFish startGlassFish(String serverId,
                                           ClassLoader bootstrapClassLoader,
                                           Properties bootstrapProperties,
                                           Properties glassfishProperties) throws Exception {
        GlassFish gf = getGlassFish(serverId, bootstrapClassLoader,
                bootstrapProperties, glassfishProperties);
        if (gf.getStatus() != GlassFish.Status.STARTED) {
            long startTime = System.currentTimeMillis();
            gf.start();
            logger.logp(Level.INFO, "PluginUtil", "startGlassFish", "Started GlassFish ServerId = {0}, " +
                    "GlassFish = {1}, TimeTaken = {2} ms",
                    new Object[]{serverId, gf, System.currentTimeMillis() - startTime});
        }
        return gf;
    }

    public static void stopGlassFish(String serverId) throws Exception {
        GlassFish gf = gfMap.remove(serverId);
        if (gf != null && gf.getStatus().equals(GlassFish.Status.STARTED)) {
            gf.stop();
            if (gfr != null) {
                gfr.shutdown();
            }
        }
        logger.logp(Level.INFO, "PluginUtil", "stopGlassFish",
                "Stopped GlassFish ServerId = {0}, GlassFish = {1}",
                new Object[]{serverId, gf});
    }

    public static void doDeploy(String serverId, ClassLoader cl,
                                Properties bootstrapProperties,
                                Properties glassfishProperties,
                                File archive, String[] deploymentParameters) throws Exception {
        GlassFish gf = startGlassFish(serverId, cl, bootstrapProperties, glassfishProperties);
        // Lookup the deployer.
        Deployer deployer = gf.getService(Deployer.class);
        logger.logp(Level.FINE, "PluginUtil", "doDeploy", "Deployer = {0}", deployer);
        logger.info("Deploying [" + archive + "] with parameters " +
                (deploymentParameters!= null ? Arrays.asList(deploymentParameters).toString() : "[]"));
        String name = deployer.deploy(archive.toURI(), deploymentParameters);
        logger.logp(Level.INFO, "PluginUtil", "doDeploy", "Deployed {0}", name);
    }

    public static void doUndeploy(String serverId, ClassLoader bootstrapClassLoader,
                                  Properties bootstrapProperties,
                                  Properties glassfishProperties,
                                  String appName, String[] deploymentParameters) {
        try {
            GlassFish gf = startGlassFish(serverId, bootstrapClassLoader,
                    bootstrapProperties, glassfishProperties);
            // Lookup the deployer.
            Deployer deployer = gf.getService(Deployer.class);
            logger.logp(Level.INFO, "PluginUtil", "doUndeploy", "Deployer = {0}", deployer);

            deployer.undeploy(appName, deploymentParameters);
            logger.logp(Level.INFO, "PluginUtil", "doUndeploy", "Undeployed {0}", appName);
        } catch (Exception ex) {
            // Ignore the exception since it is undeployment.
            logger.logp(Level.WARNING, "PluginUtil", "doUndeploy", "Unable to undeploy {0}. Exception = {1}",
                    new Object[]{appName, ex.getMessage()});
        }
    }

    private static GlassFish getGlassFish(String serverId, ClassLoader bootstrapClassLoader,
                                          Properties bootstrapProperties,
                                          Properties glassfishProperties)
            throws Exception {
        GlassFish gf = gfMap.get(serverId);
        if (gf == null) {
            long startTime = System.currentTimeMillis();
            logger.logp(Level.FINE, "PluginUtil", "getGlassFish", "Creating GlassFish ServerId = {0}", serverId);
            BootstrapProperties bootstrapOptions = new BootstrapProperties(bootstrapProperties);
            gfr = gfr != null ? gfr : GlassFishRuntime.bootstrap(bootstrapOptions, bootstrapClassLoader);
/*
            GlassFishRuntime gfr = GlassFishRuntime.bootstrap(bootstrapOptions,
                    PluginUtil.class.getClassLoader());
*/
            logger.logp(Level.FINE, "PluginUtil", "getGlassFish", "Created GlassFishRuntime " +
                    "ServerId = {0}, GlassFishRuntime = {1}, TimeTaken = {2} ms",
                    new Object[]{serverId, gfr, System.currentTimeMillis() - startTime});
            GlassFishProperties gfOptions = new GlassFishProperties(glassfishProperties);
            gf = gfr.newGlassFish(gfOptions);
            logger.logp(Level.INFO, "PluginUtil", "getGlassFish", "Created GlassFish ServerId = {0}, " +
                    "BootstrapProperties = {1}, GlassFishRuntime = {2}, GlassFishProperties = {3}, " +
                    "GlassFish = {4}, GlassFish Status = {5}, TimeTaken = {6} ms",
                    new Object[]{serverId, bootstrapProperties, gfr, glassfishProperties,
                            gf, gf.getStatus(), System.currentTimeMillis() - startTime});
            gfMap.put(serverId, gf);
        }
        return gf;
    }

    public static void runCommand(String serverId, String[] commandLines)
            throws Exception {
        GlassFish gf = gfMap.get(serverId);
        if (gf != null) {
            CommandRunner cr = gf.getService(CommandRunner.class);
            for (String commandLine : commandLines) {
                String[] split = commandLine.split(" ");
                String command = split[0].trim();
                String[] commandParams = null;
                if (split.length > 1) {
                    commandParams = new String[split.length - 1];
                    for (int i = 1; i < split.length; i++) {
                        commandParams[i - 1] = split[i].trim();
                    }
                }
                try {
                    CommandResult result = commandParams == null ?
                            cr.run(command) : cr.run(command, commandParams);
                    logger.logp(Level.INFO, "PluginUtil", "runCommand",
                            "Ran command [{0}]. Exit Code [{1}], Output = [{2}]",
                            new Object[]{commandLine, result.getExitStatus(), result.getOutput()});
                } catch (Exception ex) {
                    System.out.println(ex.getMessage());
                    ex.printStackTrace();
                }
            }
        }
    }

}
