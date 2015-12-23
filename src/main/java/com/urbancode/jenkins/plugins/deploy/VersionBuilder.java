/*
 * Licensed Materials - Property of IBM Corp.
 * IBM UrbanCode Deploy
 * IBM AnthillPro
 * (c) Copyright IBM Corporation 2002, 2016. All Rights Reserved.
 *
 * U.S. Government Users Restricted Rights - Use, duplication or disclosure restricted by
 * GSA ADP Schedule Contract with IBM Corp.
 */
package com.urbancode.jenkins.plugins.deploy;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor.FormException;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;

import java.io.IOException;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

/**
 * Builder to create a component version in IBM UrbanCode Deploy
 * Sample {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder, {@link
 * DescriptorImplnewInstance(StaplerRequest)} is invoked and a new
 * {@link VersionBuilder} is created. The created instance is persisted to the
 * project configuration XML by using XStream, so this allows you to use
 * instance fields (like {@link name}) to remember the configuration.
 *
 * <p>
 * When a build is performed, the {@link perform(AbstractBuild, Launcher,
 * BuildListener)} method will be invoked.
 *
 */
public class VersionBuilder extends Builder {
    public static final GlobalConfig.GlobalConfigDescriptor GLOBALDESCRIPTOR = GlobalConfig.getGlobalConfigDescriptor();

    private String siteName;
    private String component;
    private String version;
    private String baseDir;
    private String fileIncludePatterns;
    private String fileExcludePatterns;
    private String properties;
    private String description;
    private Boolean incremental = false;

    /**
     * Constructor used for data-binding fields from the corresponding
     * config.jelly
     *
     * @param siteName The profile name of the UrbanDeploy site
     * @param component The name of the component on the UCD server
     * @param version The name of the component version on the UCD server
     * @param baseDir The base directory to pull artifacts from
     * @param fileIncludePatterns A list of patterns to include
     * @param fileExcludePatterns A list of patterns to exclude
     * @param properties Any properties to create on the new version
     * @param description A description for the new component version
     * @param incremental Creates an incremental component version
     */
    @DataBoundConstructor
    public VersionBuilder(
            String siteName,
            String component,
            String version,
            String baseDir,
            String fileIncludePatterns,
            String fileExcludePatterns,
            String properties,
            String description,
            Boolean incremental)
    {
        this.siteName = siteName;
        this.component = component;
        this.version = version;
        this.baseDir = baseDir;
        this.fileIncludePatterns = fileIncludePatterns;
        this.fileExcludePatterns = fileExcludePatterns;
        this.properties = properties;
        this.description = description;
        this.incremental = incremental;
    }

    /*
     * Accessors and mutators required for data-binding access
     */

    public String getSiteName() {
        String name = siteName;
        if (name == null) {
            UrbanDeploySite[] sites = GLOBALDESCRIPTOR.getSites();
            if (sites.length > 0) {
                name = sites[0].getProfileName();
            }
        }
        return name;
    }

    public void setSiteName(String siteName) {
        this.siteName = siteName;
    }

    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String versionName) {
        this.version = versionName;
    }

    public String getBaseDir() {
        return baseDir;
    }

    public void setBaseDir(String baseDir) {
        this.baseDir = baseDir;
    }

    public String getFileIncludePatterns() {
        if (fileIncludePatterns == null || fileIncludePatterns.trim().length() == 0) {
            fileIncludePatterns = "**/*";
        }
        return fileIncludePatterns;
    }

    public void setFileIncludePatterns(String fileIncludePatterns) {
        this.fileIncludePatterns = fileIncludePatterns;
    }

    public String getFileExcludePatterns() {
        return fileExcludePatterns;
    }

    public void setFileExcludePatterns(String fileExcludePatterns) {
        this.fileExcludePatterns = fileExcludePatterns;
    }

    public String getProperties() {
        return properties;
    }

    public void setProperties(String properties) {
        this.properties = properties;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setIncremental(boolean incremental) {
        this.incremental = incremental;
    }

    public boolean isIncremental() {
        return incremental;
    }

    /**
     * This method returns the configured UrbanDeploySite object which match the
     * siteName of the UrbanDeployPublisher instance. (see Manage Hudson and
     * System Configuration point UrbanDeploy)
     *
     * @return the matching UrbanDeploySite or null
     */
    public UrbanDeploySite getSite() {
        UrbanDeploySite[] sites = GLOBALDESCRIPTOR.getSites();
        if (siteName == null && sites.length > 0) {
            // default
            return sites[0];
        }
        for (UrbanDeploySite site : sites) {
            if (site.getDisplayName().equals(siteName)) {
                return site;
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * @param build
     * @param launcher
     * @param listener
     * @return A boolean to represent if the build can continue
     * @throws InterruptedException
     * @throws java.io.IOException {@inheritDoc}
     * @see hudson.tasks.BuildStep#perform(hudson.model.Build, hudson.Launcher,
     *      hudson.model.BuildListener)
     */
    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener)
    throws AbortException, InterruptedException, IOException {
        if (build.getResult() == Result.FAILURE || build.getResult() == Result.ABORTED) {
            throw new AbortException("Skip artifacts upload to IBM UrbanCode Deploy - build failed or aborted.");
        }

        EnvVars envVars = build.getEnvironment(listener);
        UrbanDeploySite udSite = getSite();
        VersionHelper versionHelper = new VersionHelper(udSite.getUri(), udSite.getClient());

        String resolvedComponent = envVars.expand(component);
        String resolvedVersion = envVars.expand(version);
        String resolvedBaseDir = envVars.expand(baseDir);
        String resolvedFileIncludePatterns = envVars.expand(fileIncludePatterns);
        String resolvedFileExcludePatterns = envVars.expand(fileExcludePatterns);
        String resolvedProperties = envVars.expand(properties);
        String resolvedDescription = envVars.expand(description);
        String versionType = "FULL";

        if (incremental) {
            versionType = "INCREMENTAL";
        }

        PublishArtifactsCallable task = new PublishArtifactsCallable(
                versionHelper,
                resolvedBaseDir,
                resolvedFileIncludePatterns,
                resolvedFileExcludePatterns,
                resolvedComponent,
                resolvedVersion,
                resolvedDescription,
                versionType,
                listener);

        // task must run on the correct channel when pulling version files
        task.callOnChannel(launcher.getChannel());

        // create properties on version
        versionHelper.setComponentVersionProperties(
                resolvedComponent,
                resolvedVersion,
                resolvedProperties,
                listener);

        // add component version link
        String linkName = "Jenkins Job " + build.getDisplayName();
        String linkUrl = Hudson.getInstance().getRootUrl() + build.getUrl();
        listener.getLogger().println("Adding Jenkins job link " + linkUrl);
        versionHelper.addLinkToComp(resolvedComponent, resolvedVersion, linkName, linkUrl);

        return true;
    }

    /**
     * This class holds the metadata for the VersionBuilder and allows it's data
     * fields to persist
     *
     */
    @Extension
    public static class VersionBuilderDescriptor extends BuildStepDescriptor<Builder> {

        public VersionBuilderDescriptor() {
            load();
        }

        /**
         * Return the location of the help document for this builder.
         * <p/>
         * {@inheritDoc}
         *
         * @return {@inheritDoc}
         * @see hudson.model.Descriptor#getHelpFile()
         */
        @Override
        public String getHelpFile() {
            return "/plugin/ibm-ucdeploy-publisher/help-versionbuilder.html";
        }

        /**
         * Get all configured UrbanDeploySite objects
         *
         * @return The array of configured UrbanDeploySite objects
         */
        public UrbanDeploySite[] getSites() {
            return GLOBALDESCRIPTOR.getSites();
        }

        /**
         * Bind VersionBuilder data fields to user defined values {@inheritDoc}
         *
         * @param req {@inheritDoc}
         * @param formData {@inheritDoc}
         * @return {@inheritDoc}
         * @see hudson.model.Descriptor#configure(org.kohsuke.stapler.StaplerRequest)
         */
        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            req.bindJSON(this, formData);
            save();
            return super.configure(req, formData);
        }

        /**
         * {@inheritDoc}
         *
         * @return {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "UrbanCode Deploy - Create Component Version";
        }

        /**
         * {@inheritDoc}
         *
         * @param jobType {@inheritDoc}
         * @return {@inheritDoc}
         */
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }
}
