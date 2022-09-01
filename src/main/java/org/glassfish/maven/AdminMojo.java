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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.lang.reflect.Method;

/**
 * This Mojo runs post startup adminstrative commands on the Embedded GlassFish.
 * The commands should be specified in the commands string array.
 *
 * @author bhavanishankar@dev.java.net
 */
@Mojo(name = "admin", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST)
public class AdminMojo extends AbstractServerMojo {

    /**
     * The set of post startup commands to be run on Embedded GlassFish.
     * <p/>
     * For example:
     * <pre>
     * &lt;commands&gt;
     *      &lt;command&gt;set configs.config.server-config.network-config.protocols.protocol.http-listener.http.websockets-support-enabled=true&lt;/command&gt;
     * &lt;/commands&gt;
     * </pre>
     */
    @Parameter(property = "commands")
    protected String[] commands;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            runCommand(serverID, getClassLoader(), commands);
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new MojoExecutionException(ex.getMessage(), ex);
        }
    }

    public void runCommand(String serverId, ClassLoader cl,
                           String[] commandLines) throws Exception {
        Class clazz = cl.loadClass(PluginUtil.class.getName());
        Method m = clazz.getMethod("runCommand", new Class[]{
                String.class, String[].class});
        m.invoke(null, new Object[]{serverId, commandLines});
    }

}
