/*
 * Licensed Materials - Property of IBM Corp.
 * IBM UrbanCode Release
 * IBM UrbanCode Deploy
 * IBM UrbanCode Build
 * IBM AnthillPro
 * (c) Copyright IBM Corporation 2002, 2016. All Rights Reserved.
 *
 * U.S. Government Users Restricted Rights - Use, duplication or disclosure restricted by
 * GSA ADP Schedule Contract with IBM Corp.
 */
package com.urbancode.jenkins.plugins.ucdeploy;

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

import jenkins.tasks.SimpleBuildStep;

import java.io.IOException;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import com.urbancode.jenkins.plugins.ucdeploy.ComponentHelper.CreateComponentBlock;
import com.urbancode.jenkins.plugins.ucdeploy.ProcessHelper.CreateProcessBlock;
import com.urbancode.jenkins.plugins.ucdeploy.DeliveryHelper.DeliveryBlock;
import com.urbancode.jenkins.plugins.ucdeploy.DeliveryHelper.Pull;
import com.urbancode.jenkins.plugins.ucdeploy.DeliveryHelper.Push;
import com.urbancode.jenkins.plugins.ucdeploy.DeployHelper;
import com.urbancode.jenkins.plugins.ucdeploy.DeployHelper.DeployBlock;
import com.urbancode.jenkins.plugins.ucdeploy.DeployHelper.CreateSnapshotBlock;
import com.urbancode.jenkins.plugins.ucdeploy.VersionHelper;
import com.urbancode.jenkins.plugins.ucdeploy.VersionHelper.VersionBlock;;

public class UCDeployPublisher extends Builder implements SimpleBuildStep{

    public static final GlobalConfig.GlobalConfigDescriptor GLOBALDESCRIPTOR = GlobalConfig.getGlobalConfigDescriptor();

    private String siteName;
    private VersionBlock component;
    private DeployBlock deploy;

    /**
     * Constructor used for data-binding fields from the corresponding
     * config.jelly
     *
     * @param siteName The profile name of the UrbanDeploy site
     * @param component The object holding the Create Version Block structure
     * @param deploy The object holding the Deploy Block structure
     */
    @DataBoundConstructor
    public UCDeployPublisher(String siteName, VersionBlock component, DeployBlock deploy) {
        this.siteName = siteName;
        this.component = component;
        this.deploy = deploy;
    }

    /*
     * Accessors and mutators required for data-binding access
     */

    public String getSiteName() {
        String name = siteName;
        if (name == null) {
            UCDeploySite[] sites = GLOBALDESCRIPTOR.getSites();
            if (sites.length > 0) {
                name = sites[0].getProfileName();
            }
        }
        return name;
    }

    public VersionBlock getComponent() {
        return component;
    }

    public Boolean componentChecked() {
        if (getComponent() == null) {
            return false;
        }
        else {
            return true;
        }
    }

    public String getComponentName() {
        String componentName = getComponent().getComponentName();
        
        if (componentName == null) {
            return "";
        }
        else {
            return componentName;
        }
    }

    public CreateComponentBlock getCreateComponent() {
        return getComponent().getCreateComponentBlock();
    }

    public Boolean createComponentChecked() {
        if (getCreateComponent() == null) {
            return false;
        }
        else {
            return true;
        }
    }

    public String getComponentTemplate() {
        String componentTemplate = getCreateComponent().getComponentTemplate();
        
        if (componentTemplate == null) {
            return "";
        }
        else {
            return componentTemplate;
        }
    }

    public String getComponentApplication() {
        String componentApplication = getCreateComponent().getComponentApplication();
        
        if (componentApplication == null) {
            return "";
        }
        else {
            return componentApplication;
        }
    }

    public DeliveryBlock getDelivery() {
        return getComponent().getDeliveryBlock();
    }

    public String getDeliveryType() {
        String deliveryType = getDelivery().getDeliveryType().name();
        
        if (deliveryType == null) {
            return "";
        }
        else {
            return deliveryType;
        }
    }

    public String getPushVersion() {
        String pushVersion = ((Push)getDelivery()).getPushVersion();
        
        if (pushVersion == null) {
            return "";
        }
        else {
            return pushVersion;
        }
    }

    public String getBaseDir() {
        String baseDir = ((Push)getDelivery()).getBaseDir();
        
        if (baseDir == null) {
            return "";
        }
        else {
            return baseDir;
        }
    }

    public String getFileIncludePatterns() {
        String fileIncludePatterns = ((Push)getDelivery()).getFileIncludePatterns();
        
        if (fileIncludePatterns == null) {
            return "";
        }
        else {
            return fileIncludePatterns;
        }
    }

    public String getFileExcludePatterns() {
        String fileExcludePatterns = ((Push)getDelivery()).getFileExcludePatterns();
        
        if (fileExcludePatterns == null) {
            return "";
        }
        else {
            return fileExcludePatterns;
        }
    }

    public String getPushProperties() {
        String pushProperties = ((Push)getDelivery()).getPushProperties();
        
        if (pushProperties == null) {
            return "";
        }
        else {
            return pushProperties;
        }
    }

    public String getPushDescription() {
        String pushDescription = ((Push)getDelivery()).getPushDescription();
        
        if (pushDescription == null) {
            return "";
        }
        else {
            return pushDescription;
        }
    }

    public Boolean getPushIncremental() {
        if (((Push)getDelivery()).getPushIncremental() == null) {
            return false;
        }
        else {
            return ((Push)getDelivery()).getPushIncremental();
        }
    }

    public String getPullProperties() {
        String pullProperties = ((Pull)getDelivery()).getPullProperties();
        
        if (pullProperties == null) {
            return "";
        }
        else {
            return pullProperties;
        }
    }

    public String getpullSourceType() {
        String pullSourceType = ((Pull)getDelivery()).getPullSourceType();
        
        if (pullSourceType == null) {
            return "";
        }
        else {
            return pullSourceType;
        }
    }

    public String getPullSourceProperties() {
        String pullSourceProperties = ((Pull)getDelivery()).getPullSourceProperties();
        
        if (pullSourceProperties == null) {
            return "";
        }
        else {
            return pullSourceProperties;
        }
    }

    public Boolean getPullIncremental() {
        if (((Pull)getDelivery()).getPullIncremental() == null) {
            return false;
        }
        else {
            return ((Pull)getDelivery()).getPullIncremental();
        }
    }

    public DeployBlock getDeploy() {
        return deploy;
    }

    public Boolean deployChecked() {
        if (getDeploy() == null) {
            return false;
        }
        else {
            return true;
        }
    }

    public String getDeployApp() {
        String deployApp = getDeploy().getDeployApp();
        
        if (deployApp == null) {
            return "";
        }
        else {
            return deployApp;
        }
    }

    public String getDeployEnv() {
        String deployEnv = getDeploy().getDeployEnv();
        
        if (deployEnv == null) {
            return "";
        }
        else {
            return deployEnv;
        }
    }

    public String getDeployProc() {
        String deployProc = getDeploy().getDeployProc();
        
        if (deployProc == null) {
            return "";
        }
        else {
            return deployProc;
        }
    }

    public CreateProcessBlock getCreateProcess() {
        return getDeploy().getCreateProcessBlock();
    }

    public Boolean createProcessChecked() {
        if (getCreateProcess() == null) {
            return false;
        }
        else {
            return true;
        }
    }


    public String getProcessComponent() {
        String processComponent = getCreateProcess().getProcessComponent();
        
        if (processComponent == null) {
            return "";
        }
        else {
            return processComponent;
        }
    }

    public CreateSnapshotBlock getCreateSnapshot() {
        return getDeploy().getCreateSnapshotBlock();
    }

    public Boolean createSnapshotChecked() {
        if (getCreateSnapshot() == null) {
            return false;
        }
        else {
            return true;
        }
    }


    public String getSnapshotName() {
        String snapshotName = getCreateSnapshot().getSnapshotName();
        
        if (snapshotName == null) {
            return "";
        }
        else {
            return snapshotName;
        }
    }

    public String getDeployVersions() {
        String deployVersions = getDeploy().getDeployVersions();
        
        if (deployVersions == null) {
            return "";
        }
        else {
            return deployVersions;
        }
    }

    public Boolean getDeployOnlyChanged() {
        if (getDeploy().getDeployOnlyChanged() == null) {
            return false;
        }
        else {
            return getDeploy().getDeployOnlyChanged();
        }
    }

    /**
     * This method returns the configured UCDeploySite object which match the
     * siteName of the UCDeployPublisherer instance. (see Manage Hudson and
     * System Configuration point UrbanDeploy)
     *
     * @return the matching UCDeploySite or null
     */
    public UCDeploySite getSite() {
        UCDeploySite[] sites = GLOBALDESCRIPTOR.getSites();
        if (siteName == null && sites.length > 0) {
            // default
            return sites[0];
        }
        for (UCDeploySite site : sites) {
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
        UCDeploySite udSite = getSite();

        if (componentChecked()) {
            VersionHelper versionHelper = new VersionHelper(udSite.getUri(), udSite.getClient(), listener, envVars);
            versionHelper.createVersion(getComponent(),
                                        "Jenkins Build " + build.getDisplayName(),
                                        Hudson.getInstance().getRootUrl() + build.getUrl());
        }

        if (deployChecked()) {
            DeployHelper deployHelper = new DeployHelper(udSite.getUri(), udSite.getClient(), listener, envVars);
            deployHelper.deployVersion(getDeploy());
        }
    }

    /**
     * This class holds the metadata for the Publisher and allows it's data
     * fields to persist
     *
     */
    @Extension
    public static class UCDeployPublisherDescriptor extends BuildStepDescriptor<Builder> {

        public UCDeployPublisherDescriptor() {
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
            return "/plugin/ibm-ucdeploy-build-steps/publish.html";
        }

        /**
         * Get all configured UCDeploySite objects
         *
         * @return The array of configured UCDeploySite objects
         */
        public UCDeploySite[] getSites() {
            return GLOBALDESCRIPTOR.getSites();
        }

        /**
         * Bind data fields to user defined values {@inheritDoc}
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
            return "Publish Artifacts to IBM UrbanCode Deploy";
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