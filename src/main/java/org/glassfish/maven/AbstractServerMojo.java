/*
 * Copyright (c) 2023,2025 Contributors to the Eclipse Foundation.
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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.metadata.ResolutionGroup;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * @author bhavanishankar@dev.java.net
 */
public abstract class AbstractServerMojo extends AbstractMojo {

    // Only PluginUtil has access to org.glassfish.simpleglassfishapi.Constants
    // Hence declare the param names here.
    public final static String PLATFORM_KEY = "GlassFish_Platform";
    public final static String INSTANCE_ROOT_PROP_NAME = "com.sun.aas.instanceRoot";
    public static final String INSTALL_ROOT_PROP_NAME = "com.sun.aas.installRoot";
    public static final String CONFIG_FILE_URI_PROP_NAME = "org.glassfish.embeddable.configFileURI";
    private static final String NETWORK_LISTENER_KEY = "embedded-glassfish-config." +
            "server.network-config.network-listeners.network-listener.%s";

    public static String thisArtifactId = "org.glassfish.embedded:embedded-glassfish-maven-plugin";

    private static String SHELL_JAR = "lib/embedded/glassfish-embedded-static-shell.jar";
    private static String FELIX_JAR = "osgi/felix/bin/felix.jar";

    private static final String EMBEDDED_GROUP_ID = "org.glassfish.main.extras";
    private static final String EMBEDDED_ALL = "glassfish-embedded-all";
    private static final String EMBEDDED_ARTIFACT_PREFIX = "glassfish-embedded-";

    private static final String GF_API_GROUP_ID = "org.glassfish.main.common";
    private static final String GF_API_ARTIFACT_ID = "simple-glassfish-api";
    private static final String DEFAULT_GF_VERSION = "7.0.0";
    private static String gfVersion;

    /*******************************************
     * Parameters supplied by configuration
     ******************************************/

    /**
     * Identifier of the Embedded GlassFish server.
     */
    @Parameter(property = "serverID", defaultValue = "maven")
    protected String serverID;

    /**
     * <b><i>Note : Using &lt;ports&gt; configuration is preferred over this configuration.</b></i>
     * <p/>
     * Specify the HTTP port number.
     * <p/>
     * For example:
     * &lt;port&gt;8080&lt;/port&gt;
     * <p/>
     * This setting is ignored when configFile option is used.
     * <p/>
     * If no ports and no port configuration is defined, the value of 8080 is used as a default. If you want to disable
     * all HTTP listeners, specify an empty &lt;ports&gt; configuration.
     */
    @Parameter(property = "port", defaultValue = "-1")
    protected int port;


    /**
     * Location of valid GlassFish installation.
     */
    @Parameter(property = "installRoot")
    protected String installRoot;

    /**
     * Location of valid GlassFish domain.
     */
    @Parameter(property = "instanceRoot")
    protected String instanceRoot;

    /**
     * Location of custom configuration file (i.e., location of custom domain.xml).
     */
    @Parameter(property = "configFile")
    protected String configFile;

    /**
     * Specify whether the custom configuration file or config/domain.xml at
     * the specified instance root is operated read only or not.
     */
    @Parameter(property = "configFileReadOnly", defaultValue = "true")
    protected Boolean configFileReadOnly;

    /**
     * Specify the port numbers for the network listeners.
     * <p/>
     * Built-in domain.xml has HTTP and HTTPS network listeners by names
     * http-listener and https-listener respectively.
     * That allows you to configure the ports like this:
     * <p/>
     * <pre>
     * &lt;ports&gt;
     *      &lt;http-listener&gt;8080&lt;/http-listener&gt;
     *      &lt;https-listener&gt;8181&lt;/https-listener&gt;
     * &lt;/ports&gt;
     * </pre>
     * <p/>
     * If you are using custom domain.xml, you can either configure the ports
     * directy in your domain.xml or configure using this configuration parameter by
     * correctly specifying port numbers for the the names of the network-listener element
     * of your domain.xml.
     *
     */
    @Parameter
    protected Map<String, String> ports;

    /**
     * Specify the set of properties required to bootstrap GlassFishRuntime.
     * For example:
     * <pre>
     * &lt;bootstrapProperties&gt;
     *      &lt;property>GlassFish_Platform=felix&lt;/property&gt;
     * &lt;/bootstrapProperties&gt;
     * </pre>
     */
    @Parameter
    protected List<String> bootstrapProperties;

    /**
     * Specify the location of the properties file which has the properties required to bootstrap GlassFishRuntime.
     * For example:
     * <p/>
     * &lt;bootstrapPropertiesFile&gt;bootstrap.properties&lt;/bootstrapPropertiesFile&gt;
     * <p/>
     * where bootstrap.properties is a file containing the bootstrap properties.
     */
    @Parameter
    protected File bootstrapPropertiesFile;

    /**
     * Specify the set of properties required to create a new Embedded GlassFish.
     * <p/>
     * For example:
     * <pre>
     * &lt;glassfishProperties&gt;
     *      &lt;property>embedded-glassfish-config.server.jms-service.jms-host.default_JMS_host.port=17676&lt;/property&gt;
     * &lt;/glassfishProperties&gt;
     * </pre>
     */
    @Parameter
    protected List<String> glassfishProperties;

    /**
     * Specify the location of the properties file which has the properties required to create a new GlassFish.
     * For example:
     * <p/>
     * &lt;glassfishPropertiesFile&gt;glassfish.properties&lt;/glassfishPropertiesFile&gt;
     * <p/>
     * where glassfish.properties is a file containing the GlassFish properties.
     */
    @Parameter
    protected File glassfishPropertiesFile;

    /**
     * Specify the system properties.
     * For example:
     * <pre>
     * &lt;systemProperties&gt;
     *      &lt;property&gt;com.sun.aas.imqLib=${env.S1AS_HOME}/../mq/lib&lt;/property&gt;
     *      &lt;property&gt;com.sun.aas.imqBin=${env.S1AS_HOME}/../mq/bin&lt;/property&gt;
     * &lt;/systemProperties>
     * </pre>
     */
    @Parameter
    protected List<String> systemProperties;

    /**
     * Specify the location of the properties file which has the system properties.
     * <p/>
     * For example:
     * &lt;systemPropertiesFile&gt;/tmp/system.properties&lt;/systemPropertiesFile&gt;
     */
    @Parameter
    protected File systemPropertiesFile;

    /**
     * Specify whether the temporary file system created by Embedded GlassFish
     * should be deleted when Maven exits.
     * <p/>
     * Embedded GlassFish creates the temporary
     * file system under java.io.tmpdir unless a different directory is specified with
     * glassfish.embedded.tmpdir system property.
     */
    @Parameter(property = "autoDelete", defaultValue = "true")
    protected Boolean autoDelete;

    /**
     * @deprecated This is a deprecated and unused configuration. Likely to be removed in the next version of the plugin.
     */
    @Parameter(property = "containerType", defaultValue = "all")
    @Deprecated
    protected String containerType;

    /**
     * Version of Embedded GlassFish to download if Embedded GlassFish dependency is not provided
     */
    @Parameter(property = "glassfish.version", alias = "glassfish.version")
    protected String glassfishVersion;

    /*===============================================
     * End of parameters supplied by configuration
     ***********************************************/

    /***************************************
     * Dependencies injected by Maven
     ***************************************/

    /**
     * This is automatically injected by the Maven framework.
     */
    @Parameter(property = "localRepository", required = true)
    protected ArtifactRepository localRepository;

    /**
     * The remote repositories where artifacts are located.
     * This is automatically injected by the Maven framework.
     */
    @Parameter(property = "project.remoteArtifactRepositories")
    protected List remoteRepositories;

    /**
     * The maven project.
     */
    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;

    /**
     * This is automatically injected by the Maven framework.
     */
    @Parameter(defaultValue = "${plugin.artifacts}")
    private List<Artifact> artifacts; // pluginDependencies

    @Component
    protected MavenProjectBuilder projectBuilder;

    @Component
    protected ArtifactResolver resolver;

    /**
     * Used to construct artifacts for deletion/resolution...
     */
    @Component
    protected ArtifactFactory factory;

    @Component
    private ArtifactMetadataSource artifactMetadataSource;

    /*=======================================
     * End of dependencies injected by Maven
     ***************************************/

    // HashMap with Key=serverId, Value=Bootstrap ClassLoader
    protected static HashMap<String, ClassLoader> classLoaders = new HashMap();
    private static ClassLoader classLoader;

    public abstract void execute() throws MojoExecutionException, MojoFailureException;

    protected ClassLoader getClassLoader() throws MojoExecutionException {
/*
        URLClassLoader classLoader = classLoaders.get(serverID);
        if (classLoader != null) {
            printClassPaths("Using Existing Bootstrap ClassLoader. ServerId = " + serverID +
                    ", ClassPaths = ", classLoader);
            return classLoader;
        }
        try {
            classLoader = hasGlassFishInstallation() ? getInstalledGFClassLoader() : getUberGFClassLoader();
            classLoaders.put(serverID, classLoader);
            printClassPaths("Created New Bootstrap ClassLoader. ServerId = " + serverID
                    + ", ClassPaths = ", classLoader);
            return classLoader;
        } catch (Exception ex) {
            throw new MojoExecutionException(ex.getMessage(), ex);
        }
*/
        try {
            if (classLoader != null) {
                return classLoader;
            } else {
                classLoader = hasGlassFishInstallation() ? getInstalledGFClassLoader() : getUberGFClassLoader();
                printClassPaths("Created New Bootstrap ClassLoader. ServerId = " + serverID
                        + ", ClassPaths = ", classLoader);
            }
            return classLoader;
        } catch (Exception ex) {
            throw new MojoExecutionException(ex.getMessage(), ex);
        }
    }

    protected void cleanupClassLoader(String serverId) {
        ClassLoader cl = classLoaders.remove(serverID);
        if (cl != null) {
            System.out.println("Cleaned up ClassLoader for ServerID " + serverID);
        }
    }

    private void printClassPaths(String msg, ClassLoader classLoader) {
        System.out.println(msg);
        ClassLoader cl = classLoader;
        while (cl != null && cl instanceof URLClassLoader) {
            for (URL u : ((URLClassLoader) cl).getURLs()) {
                System.out.println("ClassPath Element : " + u);
            }
            cl = cl.getParent();
        }
    }

    // checks if the glassfish installation is present in the specified installRoot

    private boolean hasGlassFishInstallation() {
        return installRoot != null ? new File(installRoot, SHELL_JAR).exists()
                && new File(installRoot, FELIX_JAR).exists() : false;
    }

    private ClassLoader getInstalledGFClassLoader() throws Exception {
        File gfJar = new File(installRoot, SHELL_JAR);
        File felixJar = new File(installRoot, FELIX_JAR);
        URLClassLoader classLoader = new URLClassLoader(
                new URL[]{gfJar.toURI().toURL(), felixJar.toURI().toURL()}, getClass().getClassLoader());
        return classLoader;
    }

    private Artifact getUberFromSpecifiedDependency() {
        if (artifacts != null) {
            for (Artifact artifact : artifacts) {
                if (EMBEDDED_GROUP_ID.equals(artifact.getGroupId())) {
                    if (artifact.getArtifactId().startsWith(EMBEDDED_ARTIFACT_PREFIX)) {
                        return artifact;
                    }
                }
            }
        }
        return null;
    }

    private Dependency getDependencyManagementInfoForEmbeddedAll() {
        if (project.getDependencyManagement() != null) {
            for (Dependency dependency : project.getDependencyManagement().getDependencies()) {
                if (EMBEDDED_GROUP_ID.equals(dependency.getGroupId())) {
                    if (dependency.getArtifactId().equals(EMBEDDED_ALL)) {
                        return dependency;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Determines GlassFish version from Maven configuration:
     * <ol>
     * <li>If glassfishVersion parameter defined, return it</li>
     * <li>If Embedded All depenendy defined in dependency management, return its version</li>
     * <li>Returns the version of simple-glassfish-api as defined in plugin's pom - the version this plugin was built against</li>
     * </ol>
     *
     * @param gfMvnPlugin
     * @return Determined version of Embedded GlassFish All artifact
     * @throws Exception
     */
    private String getGlassfishVersion(Artifact gfMvnPlugin) throws Exception {
        if (glassfishVersion != null) {
            return glassfishVersion;
        }
        Dependency dependencyManagementInfo = getDependencyManagementInfoForEmbeddedAll();
        if (dependencyManagementInfo != null) {
            return dependencyManagementInfo.getVersion();
        }
        if (gfVersion != null) {
            return gfVersion;
        }
        ResolutionGroup resGroup = artifactMetadataSource.retrieve(
                gfMvnPlugin, localRepository, remoteRepositories);
        MavenProject pomProject = projectBuilder.buildFromRepository(resGroup.getPomArtifact(),
                remoteRepositories, localRepository);
        List<Dependency> dependencies = pomProject.getOriginalModel().getDependencies();
        for (Dependency dependency : dependencies) {
            if (GF_API_GROUP_ID.equals(dependency.getGroupId()) &&
                    GF_API_ARTIFACT_ID.equals(dependency.getArtifactId())) {
                gfVersion = dependency.getVersion();
            }
        }
        gfVersion = gfVersion != null ? gfVersion : DEFAULT_GF_VERSION;
        return gfVersion;
    }

    private ClassLoader getUberGFClassLoader() throws Exception {
        // Use the version user has configured in the plugin.
        Artifact gfUber = getUberFromSpecifiedDependency();
        ClassLoader cl = getClass().getClassLoader();
        if (gfUber == null) { // not specified as dependency, hence not there in the classloader cl.
            Artifact gfMvnPlugin = (Artifact) project.getPluginArtifactMap().get(thisArtifactId);
            String gfVersion = getGlassfishVersion(gfMvnPlugin); // get the same version of uber jar as that of simple-glassfish-api used while building this plugin.
            gfUber = factory.createArtifact(EMBEDDED_GROUP_ID, EMBEDDED_ALL,
                    gfVersion, "compile", "jar");
            resolver.resolve(gfUber, remoteRepositories, localRepository);
            cl = new URLClassLoader(
                    new URL[]{gfUber.getFile().toURI().toURL()}, getClass().getClassLoader());
        }
        return cl;
    }

    protected Properties getGlassFishProperties() {
        Properties props = new Properties();

        if (instanceRoot != null) {
            props.setProperty(INSTANCE_ROOT_PROP_NAME,
                    new File(instanceRoot).getAbsolutePath());
        }

        if (configFile != null) {
            try {
                URI configFileURI = URI.create(configFile);
                String scheme = configFileURI.getScheme();
                if (scheme == null || "file".equalsIgnoreCase(scheme)) {
                    props.setProperty(CONFIG_FILE_URI_PROP_NAME, new File(configFileURI).toURI().toString());
                } else {
                    // if it is a java.net.URI pointing to file: or jar: or http: then use it as is.
                    props.setProperty(CONFIG_FILE_URI_PROP_NAME, configFileURI.toString());
                }
            } catch (Exception ex) {
                // should never come here, but just in case...
                props.setProperty(CONFIG_FILE_URI_PROP_NAME, new File(configFile).toURI().toString());
            }
        }

        if (!configFileReadOnly) {
            props.setProperty("org.glassfish.embeddable.configFileReadOnly", "false");
        }

        if (ports == null && port == -1) {
            port = 8080;
        }
        if (port != -1 && configFile == null) {
            String httpListener = String.format(NETWORK_LISTENER_KEY, "http-listener");
            props.setProperty(httpListener + ".port", String.valueOf(port));
            props.setProperty(httpListener + ".enabled", "true");
        }

        if (ports != null) {
            for (String listenerName : ports.keySet()) {
                String portNumber = ports.get(listenerName);
                if (portNumber != null && portNumber.trim().length() > 0) {
                    String networkListener = String.format(NETWORK_LISTENER_KEY, listenerName);
                    props.setProperty(networkListener + ".port", portNumber);
                    props.setProperty(networkListener + ".enabled", "true");
                }
            }
        }

        if (!autoDelete) {
            props.setProperty("org.glassfish.embeddable.autoDelete", "false");
        }

        load(glassfishPropertiesFile, props);
        load(glassfishProperties, props);

        return props;
    }

    protected Properties getBootStrapProperties() {
        setSystemProperties();
        Properties props = new Properties();
        props.setProperty(PLATFORM_KEY, "Static");
        if (installRoot != null) {
            props.setProperty(INSTALL_ROOT_PROP_NAME,
                    new File(installRoot).getAbsolutePath());
        }
        load(bootstrapPropertiesFile, props);
        load(bootstrapProperties, props);
        return props;
    }

    private void load(List<String> stringList, Properties p) {
        if (p == null || stringList == null) {
            return;
        }
        for (String prop : stringList) {
            try {
                p.load(new StringReader(prop));
            } catch (Exception ex) {
                System.err.println(ex);
            }
        }
    }

    private void load(File propertiesFile, Properties p) {
        if (propertiesFile == null || p == null) {
            return;
        }
        FileInputStream stream = null;
        try {
            stream = new FileInputStream(propertiesFile);
            p.load(stream);
        } catch (Exception ex) {
            System.err.println(ex);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (Exception ex) {
                    System.err.println(ex);
                }
            }
        }
    }

    private void setSystemProperties() {
        Properties sysProps = new Properties();
        load(systemPropertiesFile, sysProps);
        load(systemProperties, sysProps);
        for (Object obj : sysProps.keySet()) {
            String key = (String) obj;
            String currentVal = System.getProperty(key);
            if (currentVal == null) {
                String value = sysProps.getProperty(key);
                if (value != null && value.trim().length() > 0) {
                    System.setProperty(key, value);
                    System.out.println("Set system property [" + key + " = " + value + "]");
                }
            }
        }
    }

//    private String getDefaultInstallRoot() {
//        Artifact gfMvnPlugin = (Artifact) project.getPluginArtifactMap().get(thisArtifactId);
//        String userDir = System.getProperty("user.home");
//        String fs = File.separator;
//        return new File(userDir, "." + gfMvnPlugin.getArtifactId() + fs +
//                gfMvnPlugin.getVersion()).getAbsolutePath();
//    }
//
//    private String getDefaultInstanceRoot(String installRoot) {
//        String fs = File.separator;
//        return new File(installRoot, "domains" + fs + "domain1").getAbsolutePath();
//    }

    public void startGlassFish(String serverId, ClassLoader cl, Properties bootstrapProperties,
                               Properties glassfishProperties) throws Exception {
        Class clazz = cl.loadClass(PluginUtil.class.getName());
        Method m = clazz.getMethod("startGlassFish", new Class[]{String.class,
                ClassLoader.class, Properties.class, Properties.class});
        m.invoke(null, new Object[]{serverId, cl, bootstrapProperties, glassfishProperties});
    }

    public void stopGlassFish(String serverId, ClassLoader cl) throws Exception {
        Class clazz = cl.loadClass(PluginUtil.class.getName());
        Method m = clazz.getMethod("stopGlassFish", new Class[]{String.class});
        m.invoke(null, new Object[]{serverId});
    }

}
