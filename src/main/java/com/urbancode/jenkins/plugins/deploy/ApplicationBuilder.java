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
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Descriptor.FormException;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;

import java.io.IOException;

import jenkins.tasks.SimpleBuildStep;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

public class ApplicationBuilder extends Builder implements SimpleBuildStep{

    public static final GlobalConfig.GlobalConfigDescriptor GLOBALDESCRIPTOR = GlobalConfig.getGlobalConfigDescriptor();

    private String siteName;
    private String application;
    private String description;
    private String component;

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
    public ApplicationBuilder(
            String siteName,
            String application,
            String description,
            String component)
    {
        this.siteName = siteName;
        this.application = application;
        this.description = description;
        this.component = component;
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

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
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
     *      hudson.model.TaskListener)
     */
    @Override
    public void perform(final Run<?, ?> build, FilePath workspace, Launcher launcher, final TaskListener listener)
    throws AbortException, InterruptedException, IOException {
        if (build.getResult() == Result.FAILURE || build.getResult() == Result.ABORTED) {
            throw new AbortException("Skip artifacts upload to IBM UrbanCode Deploy - build failed or aborted.");
        }

        EnvVars envVars = build.getEnvironment(listener);
        UrbanDeploySite udSite = getSite();
        ApplicationHelper appHelper = new ApplicationHelper(udSite.getUri(), udSite.getClient(), listener);

        String resolvedApp = envVars.expand(application);
        String resolvedDescription = envVars.expand(description);
        String resolvedComponent = envVars.expand(component);

        appHelper.createAppWithComponent(resolvedApp, resolvedDescription, resolvedComponent);
    }

    /**
     * This class holds the metadata for the VersionBuilder and allows it's data
     * fields to persist
     *
     */
    @Extension
    public static class AppBuilderDescriptor extends BuildStepDescriptor<Builder> {

        public AppBuilderDescriptor() {
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
            return "/plugin/ibm-ucdeploy-build-steps/appbuilder.html";
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
            return "UrbanCode Deploy - Create Application";
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