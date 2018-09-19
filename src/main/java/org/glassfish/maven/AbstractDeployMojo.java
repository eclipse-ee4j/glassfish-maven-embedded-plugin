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
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * @author bhavanishankar@dev.java.net
 */
public abstract class AbstractDeployMojo extends AbstractServerMojo {
    /**
     * <b><i>Note : &lt;deploymentParams&gt; configuration can be used instead of this.</i></b>
     * <p/>
     * Name of the application.
     *
     * @parameter expression="${name}" default-value="myapp"
     */
    protected String name;

    /**
     * <b><i>Note : &lt;deploymentParams&gt; configuration can be used instead of this.</i></b>
     * <p/>
     * Context root of the web application.
     *
     * @parameter expression="${contextRoot}"
     */
    protected String contextRoot;

    /**
     * <b><i>Note : &lt;deploymentParams&gt; configuration can be used instead of this.</i></b>
     * <p/>
     * Specify whether the JSPs should be precompiled during deployment.
     *
     * @parameter expression="${precompileJsp}"
     */
    protected Boolean precompileJsp;

    /**
     * <b><i>Note : &lt;deploymentParams&gt; configuration can be used instead of this.</i></b>
     * <p/>
     * Name of the database vendor.
     *
     * @parameter expression="${dbVendorName}"
     */
    protected String dbVendorName;

    /**
     * <b><i>Note : &lt;deploymentParams&gt; configuration can be used instead of this.</i></b>
     * <p/>
     * Specify whether the tables should be created during deployment.
     *
     * @parameter expression="${createTables}"
     */
    protected Boolean createTables;

    /**
     * <b><i>Note : &lt;deploymentParams&gt; configuration can be used instead of this.</i></b>
     * <p/>
     * A comma-separated list of library JAR files.
     *
     * @parameter expression="${libraries}"
     */
    protected String libraries;
    /**
     * Build directory of the maven project. Automatically injected by
     * Maven framework.
     *
     * @parameter expression="${project.build.directory}"
     */
    String buildDirectory;

    /**
     * Base directory of the maven project. Automatically injected by
     * Maven framework.
     *
     * @parameter expression="${basedir}"
     */
    String baseDirectory;

    /**
     * Name of the file to be deployed to Embedded GlassFish.
     * <p/>
     * Use app configuration instead of this.
     *
     * @parameter expression="${project.build.finalName}"
     */
    String fileName;

    /**
     * Location of the application to be deployed.
     * <p/>
     * Location could be a Java EE file archive or a directory.
     *
     * @parameter expression="${app}"
     */
    protected String app;

    /**
     * Deployment parameters to be used while deploying the application to Embedded GlassFish.
     * <p/>
     * The deployment parameters are same as how they would be passed to
     * 'asadmin deploy' command while using standalone GlassFish.
     * <p/>
     * For example:
     * <pre>
     * &lt;deploymentParams&gt;
     *      &lt;param&gt;--contextroot=greetings&lt;/param&gt;
     *      &lt;param&gt;--name=test&lt;/param&gt;*
     *      &lt;param&gt;--createtables=true&lt;/param&gt;
     *      &lt;param&gt;--force=true&lt;/param&gt;
     *      &lt;param&gt;--precompilejsp=true&lt;/param&gt;
     * &lt;/deploymentParams&gt;
     * </pre>
     *
     * @parameter expression="${deploymentParams}"
     */
    protected String[] deploymentParams;

    /**
     * Undeployment parameters to be used while undeploying the application
     * from Embedded GlassFish.
     * <p/>
     * The undeployment parameters are same as how they would be passed to
     * 'asadmin undeploy' command while using standalone GlassFish.
     * <p/>
     * For example:
     * <pre>
     * &lt;undeploymentParams&gt;
     *      &lt;param&gt;--droptables=true&lt;/param&gt;
     * &lt;/undeploymentParams&gt;
     * </pre>
     *
     * @parameter expression="${undeploymentParams}"
     */
    protected String[] undeploymentParams;

    public abstract void execute() throws MojoExecutionException, MojoFailureException;

    protected String[] getDeploymentParameters() {
        List<String> deployParams = new ArrayList();
        set(deployParams, "--name", name);
        set(deployParams, "--force", "true");
        set(deployParams, "--contextroot", contextRoot);
        set(deployParams, "--precompilejsp", precompileJsp);
        set(deployParams, "--dbvendorname", dbVendorName);
        set(deployParams, "--createtables", createTables);
        set(deployParams, "--libraries", libraries);
        if (deploymentParams != null) {
            for (String p : deploymentParams) {
                deployParams.add(p);
            }
        }
        return deployParams.toArray(new String[0]);
    }

    protected String[] getDeploymentParameters(Properties goalConfiguration) {
        List<String> deployParams = new ArrayList();
        set(deployParams, "--name", goalConfiguration.getProperty("name"));
        set(deployParams, "--force", "true");
        set(deployParams, "--contextroot", goalConfiguration.getProperty("contextRoot"));
        set(deployParams, "--precompilejsp", goalConfiguration.getProperty("precompileJsp"));
        set(deployParams, "--dbvendorname", goalConfiguration.getProperty("dbVendorName"));
        set(deployParams, "--createtables", goalConfiguration.getProperty("createTables"));
        set(deployParams, "--libraries", goalConfiguration.getProperty("libraries"));
        List<String> deploymentParams = (List<String>) goalConfiguration.get("deploymentParams");
        for (String p : deploymentParams) {
            deployParams.add(p);
        }
        return deployParams.toArray(new String[0]);
    }

    protected String[] getUndeploymentParameters() {
        List<String> undeployParams = new ArrayList();
        if (undeploymentParams != null) {
            for (String p : undeploymentParams) {
                undeployParams.add(p);
            }
        }
        return undeployParams.toArray(new String[0]);
    }

    /**
     * Add the paramName:paramValue key-value pair into params, if both
     * paramName and paramValue are non null.
     *
     * @param params     Map where the paramName:Value to be added
     * @param paramName  Name of the parameter
     * @param paramValue Value of the parameter.
     */
    void set(List<String> params, String paramName, Object paramValue) {
        if (paramValue != null && paramName != null) {
            params.add(paramName + "=" + paramValue.toString());
        }
    }

    protected String getApp() {
        return getApp(app);
    }

    protected String getApp(String app) {
        if (app != null) {
            return new File(app).isAbsolute() ? app : baseDirectory + File.separator + app;
        } else {
            return buildDirectory + File.separator + fileName + ".war"; // TODO :: use pom.xml's packaging type.
        }
    }
    
    protected void doDeploy(String serverId, ClassLoader cl, Properties bootstrapProps,
                            Properties glassfishProperties,
                            File archive, String[] deploymentParams) throws Exception {
        Class clazz = cl.loadClass(PluginUtil.class.getName());
        Method m = clazz.getMethod("doDeploy", new Class[]{String.class,
                ClassLoader.class, Properties.class, Properties.class, File.class, String[].class});
        m.invoke(null, new Object[]{serverId, cl, bootstrapProps, glassfishProperties,
                archive, deploymentParams});
    }

    protected void doUndeploy(String serverId, ClassLoader cl, Properties bootstrapProps,
                              Properties glassfishProperties,
                              String appName, String[] undeploymentParams) throws Exception {
        Class clazz = cl.loadClass(PluginUtil.class.getName());
        Method m = clazz.getMethod("doUndeploy", new Class[]{String.class,
                ClassLoader.class, Properties.class, Properties.class, String.class, String[].class});
        m.invoke(null, new Object[]{serverId, cl, bootstrapProps, glassfishProperties,
                appName, undeploymentParams});
    }

}
