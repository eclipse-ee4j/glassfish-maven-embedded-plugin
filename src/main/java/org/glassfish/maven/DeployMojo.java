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

import java.io.File;


/**
 * This Mojo deploys the application to the Embedded GlassFish server.
 * <p/>
 * The deployment artifact's location can be specified using 'app' configuration and
 * the deployment parameters can be specified in 'deploymentParams' configuration.
 *
 * @author bhavanishankar@dev.java.net
 */
@Mojo(name = "deploy", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST)
public class DeployMojo extends AbstractDeployMojo {

    public void execute() throws MojoExecutionException, MojoFailureException {

        try {
            // call undeploy to prevent:
            // org.jvnet.hk2.config.TransactionFailure: Keys cannot be duplicate.
            // Old value of this key property, nullwill be retained
//            doUndeploy(serverID, getClassLoader(), getBootStrapProperties(),
//                    name, new String[0]);
            doDeploy(serverID, getClassLoader(), getBootStrapProperties(),
                    getGlassFishProperties(), new File(getApp()), getDeploymentParameters());
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

}
