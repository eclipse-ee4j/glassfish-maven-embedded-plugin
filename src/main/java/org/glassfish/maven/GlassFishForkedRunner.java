/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Properties;

/**
 * Entry point for the forked GlassFish JVM launched by {@link RunMojo} when {@code fork=true}.
 * <p>
 * Reads bootstrap and GlassFish properties from a config file passed as the first argument,
 * starts GlassFish, prints {@code READY} to stdout, then processes commands from stdin until
 * a {@code STOP} command is received.
 * <p>
 * Command protocol (stdin, one command per line):
 * <pre>
 *   ADMIN &lt;command line&gt;
 *   DEPLOY &lt;archive-path&gt; [--param=value ...]
 *   UNDEPLOY &lt;appName&gt; [--param=value ...]
 *   STOP
 * </pre>
 * Response protocol (stdout):
 * <pre>
 *   READY
 *   OK [result]
 *   ERROR &lt;message&gt;
 * </pre>
 */
public class GlassFishForkedRunner {

    static final String CMD_ADMIN = "ADMIN";
    static final String CMD_DEPLOY = "DEPLOY";
    static final String CMD_UNDEPLOY = "UNDEPLOY";
    static final String CMD_STOP = "STOP";

    static final String RESP_READY = "READY";
    static final String RESP_OK = "OK";
    static final String RESP_ERROR = "ERROR";

    static final String SECTION_SERVER_ID = "serverID";
    static final String SECTION_BOOTSTRAP = "bootstrap.";
    static final String SECTION_GLASSFISH = "glassfish.prop.";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: GlassFishForkedRunner <config-file>");
            System.exit(1);
        }

        Properties config = new Properties();
        try (FileInputStream fis = new FileInputStream(new File(args[0]))) {
            config.load(fis);
        }

        String serverId = config.getProperty(SECTION_SERVER_ID, "maven");
        Properties bootstrapProps = extractPrefixed(config, SECTION_BOOTSTRAP);
        Properties glassfishProps = extractPrefixed(config, SECTION_GLASSFISH);

        PluginUtil.startGlassFish(serverId, GlassFishForkedRunner.class.getClassLoader(),
                bootstrapProps, glassfishProps);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                PluginUtil.stopGlassFish(serverId);
            } catch (Exception e) {
                System.err.println("Error stopping GlassFish during shutdown: " + e.getMessage());
            }
        }, "glassfish-shutdown-hook"));

        System.out.println(RESP_READY);
        System.out.flush();

        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = stdin.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.equals(CMD_STOP)) {
                handleStop(serverId);
                break;
            } else if (line.startsWith(CMD_ADMIN + " ")) {
                handleAdmin(serverId, line.substring(CMD_ADMIN.length() + 1));
            } else if (line.startsWith(CMD_DEPLOY + " ")) {
                handleDeploy(serverId, bootstrapProps, glassfishProps,
                        line.substring(CMD_DEPLOY.length() + 1).trim());
            } else if (line.startsWith(CMD_UNDEPLOY + " ")) {
                handleUndeploy(serverId, bootstrapProps, glassfishProps,
                        line.substring(CMD_UNDEPLOY.length() + 1).trim());
            } else {
                respond(RESP_ERROR, "Unknown command: " + line);
            }
        }
    }

    private static void handleStop(String serverId) {
        try {
            PluginUtil.stopGlassFish(serverId);
            respond(RESP_OK, null);
        } catch (Exception e) {
            respond(RESP_ERROR, e.getMessage());
        }
    }

    private static void handleAdmin(String serverId, String commandLine) {
        try {
            PluginUtil.runCommand(serverId, new String[]{commandLine});
            respond(RESP_OK, null);
        } catch (Exception e) {
            respond(RESP_ERROR, e.getMessage());
        }
    }

    private static void handleDeploy(String serverId, Properties bootstrapProps,
            Properties glassfishProps, String rest) {
        String[] parts = rest.split(" ", 2);
        File archive = new File(parts[0]);
        String[] deployParams = parts.length > 1 ? parts[1].split(" ") : new String[0];
        try {
            PluginUtil.doDeploy(serverId, GlassFishForkedRunner.class.getClassLoader(),
                    bootstrapProps, glassfishProps, archive, deployParams);
            respond(RESP_OK, null);
        } catch (Exception e) {
            respond(RESP_ERROR, e.getMessage());
        }
    }

    private static void handleUndeploy(String serverId, Properties bootstrapProps,
            Properties glassfishProps, String rest) {
        String[] parts = rest.split(" ", 2);
        String appName = parts[0];
        String[] undeployParams = parts.length > 1 ? parts[1].split(" ") : new String[0];
        try {
            PluginUtil.doUndeploy(serverId, GlassFishForkedRunner.class.getClassLoader(),
                    bootstrapProps, glassfishProps, appName, undeployParams);
            respond(RESP_OK, null);
        } catch (Exception e) {
            respond(RESP_ERROR, e.getMessage());
        }
    }

    private static void respond(String status, String message) {
        System.out.println(message != null ? status + " " + message : status);
        System.out.flush();
    }

    private static Properties extractPrefixed(Properties source, String prefix) {
        Properties result = new Properties();
        for (String key : source.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                result.setProperty(key.substring(prefix.length()), source.getProperty(key));
            }
        }
        return result;
    }
}
