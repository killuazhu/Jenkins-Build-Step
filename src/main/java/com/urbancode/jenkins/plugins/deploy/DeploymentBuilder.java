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
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Descriptor.FormException;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jenkins.tasks.SimpleBuildStep;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

/**
 * Builder to run a deployment application process in IBM UrbanCode Deploy
 * Sample {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder, {@link
 * DescriptorImplnewInstance(StaplerRequest)} is invoked and a new
 * {@link DeploymentBuilder} is created. The created instance is persisted to the
 * project configuration XML by using XStream, so this allows you to use
 * instance fields (like {@link name}) to remember the configuration.
 *
 * <p>
 * When a build is performed, the {@link perform(AbstractBuild, Launcher,
 * TaskListener)} method will be invoked.
 *
 */
public class DeploymentBuilder extends Builder implements SimpleBuildStep {
    public static final GlobalConfig.GlobalConfigDescriptor GLOBALDESCRIPTOR = GlobalConfig.getGlobalConfigDescriptor();

    private String siteName;
    private String deployApp;
    private String deployProc;
    private String componentVersions;
    private String deployEnv;
    private Boolean onlyChanged = false;
    private String snapshot;
    private String deploymentResult;

    /**
     * Constructor used for data-binding fields from the corresponding
     * config.jelly
     *
     * @param siteName The profile name of the UrbanDeploy site
     * @param deployApp The application to deploy with
     * @param deployProc The application process
     * @param componentVersions The mapping of component names to version names
     * @param deployEnv The environment to deploy in
     * @param onlyChanged Only installs changed versions
     * @param snapshot A snapshot to deploy
     */
    @DataBoundConstructor
    public DeploymentBuilder(
            String siteName,
            String deployApp,
            String deployProc,
            String componentVersions,
            String deployEnv,
            Boolean onlyChanged,
            String snapshot)
    {
        this.siteName = siteName;
        this.deployApp = deployApp;
        this.deployProc = deployProc;
        this.componentVersions = componentVersions;
        this.deployEnv = deployEnv;
        this.onlyChanged = onlyChanged;
        this.snapshot = snapshot;
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

    public void setDeployApp(String deployApp) {
        this.deployApp = deployApp;
    }

    public String getDeployApp() {
        return deployApp;
    }

    public void setDeployProc(String deployProc) {
        this.deployProc = deployProc;
    }

    public String getDeployProc() {
        return deployProc;
    }

    public void setComponentVersions(String componentVersions) {
        this.componentVersions = componentVersions;
    }

    public String getComponentVersions() {
        return componentVersions;
    }

    public void setDeployEnv(String deployEnv) {
        this.deployEnv = deployEnv;
    }

    public String getDeployEnv() {
        return deployEnv;
    }

    public void setOnlyChanged(boolean onlyChanged) {
        this.onlyChanged = onlyChanged;
    }

    public boolean isOnlyChanged() {
        return onlyChanged;
    }

    public String getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(String snapshot) {
        this.snapshot = snapshot;
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
     * @return whether or not the build can continue
     * @throws InterruptedException
     * @throws java.io.IOException {@inheritDoc}
     * @see hudson.tasks.BuildStep#perform(hudson.model.Build, hudson.Launcher,
     *      hudson.model.TaskListener)
     */
    @Override
    public void perform(final Run<?, ?> build, FilePath workspace, Launcher launcher, final TaskListener listener)
    throws AbortException, InterruptedException, IOException {
        if (build.getResult() == Result.FAILURE || build.getResult() == Result.ABORTED) {
            throw new AbortException("Skip version deployment in IBM UrbanCode Deploy - build failed or aborted.");
        }

        EnvVars envVars = build.getEnvironment(listener);

        UrbanDeploySite udSite = getSite();
        ApplicationHelper deployHelper = new ApplicationHelper(udSite.getUri(), udSite.getClient(), listener);

        String resolvedDeployApp = envVars.expand(deployApp);
        String resolvedDeployProc = envVars.expand(deployProc);
        String resolvedDeployEnv = envVars.expand(deployEnv);
        String resolvedSnapshot = envVars.expand(snapshot);

        // create component version mappings
        Map<String, List<String>> resolvedComponentVersions = new HashMap<String, List<String>>();

        for (String cvLine : componentVersions.split("\n")) {
            if(!cvLine.isEmpty() && cvLine != null) {
                int delim = cvLine.indexOf(':');

                if (delim <= 0) {
                    throw new AbortException("Component/version pairs must be of the form {Component}:{Version #}");
                }

                String component = cvLine.substring(0, delim).trim();
                component = envVars.expand(component);

                List<String> versionList = resolvedComponentVersions.get(component);

                // create new list of versions if no versions have been added
                if (versionList == null) {
                    versionList = new ArrayList<String>();
                    resolvedComponentVersions.put(component, versionList);
                }

                // update existing list of versions
                String version = cvLine.substring(delim + 1).trim();
                version = envVars.expand(version);
                versionList.add(version);
            }
        }

        if (resolvedDeployApp == null || resolvedDeployApp.trim().length() == 0) {
            throw new AbortException("Deploy Application is a required field for deployment.");
        }
        if (resolvedDeployEnv == null || resolvedDeployEnv.trim().length() == 0) {
            throw new AbortException("Deploy Environment is a required field for deployment.");
        }
        if (resolvedDeployProc == null || resolvedDeployProc.trim().length() == 0) {
            throw new AbortException("Deploy Process is a required field for deployment.");
        }

        listener.getLogger().println("Starting deployment process " + resolvedDeployProc
                + " of application " + resolvedDeployApp + " in environment " + resolvedDeployEnv);

        String requestId = deployHelper.createDefaultProcessRequest(
                resolvedDeployApp,
                resolvedDeployProc,
                resolvedComponentVersions,
                resolvedDeployEnv,
                onlyChanged,
                resolvedSnapshot);

        listener.getLogger().println("Deployment request id is: " + requestId);
        listener.getLogger().println("Deployment of application request " + requestId
                + " of application " + resolvedDeployApp + " is running.");
        long startTime = new Date().getTime();

        boolean processFinished = false;
        String deploymentResult = "";

        while (!processFinished) {
            deploymentResult = deployHelper.checkDeploymentProcessResult(requestId);

            if (!deploymentResult.equalsIgnoreCase("NONE") && !deploymentResult.isEmpty()) {
                processFinished = true;

                if (deploymentResult.equalsIgnoreCase("FAULTED")
                        || deploymentResult.equalsIgnoreCase("FAILED TO START")) {
                    throw new AbortException("Deployment process failed with result " + deploymentResult);
                }
            }

            // give application process more time to complete
            Thread.sleep(3000);
        }

        long duration = (new Date().getTime() - startTime) / 1000;

        listener.getLogger().println("Finished deployment of application request " + requestId + " for application "
                + resolvedDeployApp + " in environment " + resolvedDeployEnv + " in " + duration + " seconds");
        listener.getLogger().println("The deployment result is " + deploymentResult
                + ". See the UrbanCode Deploy deployment logs for details.");
    }

    /**
     * This class holds the metadata for DeploymentBuilder and allows it's data
     * fields to persist
     *
     */
    @Extension
    public static class DeploymentBuilderDescriptor extends BuildStepDescriptor<Builder> {

        public DeploymentBuilderDescriptor() {
            load();
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
         * Return the location of the help document for this builder.
         * <p/>
         * {@inheritDoc}
         *
         * @return {@inheritDoc}
         * @see hudson.model.Descriptor#getHelpFile()
         */
        @Override
        public String getHelpFile() {
            return "/plugin/ibm-ucdeploy-build-steps/deploybuilder.html";
        }

        /**
         * Bind DeploymentBuilder data fields to user defined values
         * {@inheritDoc}
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
            return "UrbanCode Deploy - Run Application Process";
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