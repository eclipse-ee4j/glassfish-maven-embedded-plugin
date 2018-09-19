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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * This goal is not implemented. There is no usecase/requirement in support of this goal.
 *
 * @author bhavanishankar@dev.java.net
 * @goal runscatteredarchive
 */

public class RunScatteredArchive extends AbstractDeployMojo {

    /**
     * @parameter expression="${cascade}"
     */
    Boolean cascade;
    /**
     * @parameter expression="${dropTables}"
     */
    Boolean dropTables;

    /**
     * @parameter expression="${rootdirectory}"
     * @required
     */
    protected String rootdirectory;
    /**
     * @parameter expression="${resources}"
     */
    protected String resources;

    /**
     * @parameter expression="${classpath}"
     */
    protected ArrayList<String> classpath = new ArrayList();

    /**
     * @parameter expression="${metadata}"
     */
    protected HashMap<String, File> metadata = new HashMap();

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        // TODO :: Implement once the simpleglassfishapi deployer has support for scattered archive.
        throw new MojoExecutionException("Not yet implemented. Will be implemented soon.");
    }
}
