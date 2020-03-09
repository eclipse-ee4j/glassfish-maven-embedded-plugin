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

/**
 * This Mojo starts the Embedded GlassFish with the configured parameters
 * supplied by the user in the embedded-glassfish-maven-plugin configuration.
 *
 * @author bhavanishankar@dev.java.net
 * @goal start
 * @phase pre-integration-test
 */
public class StartMojo extends AbstractServerMojo {

    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            startGlassFish(serverID, getClassLoader(), getBootStrapProperties(),
                    getGlassFishProperties());
        } catch (Exception ex) {
            throw new MojoExecutionException(ex.getMessage(), ex);
        }
    }

}
