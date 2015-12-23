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
 * Builder to import component versions in IBM UrbanCode Deploy Sample
 * {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder, {@link
 * DescriptorImplnewInstance(StaplerRequest)} is invoked and a new
 * {@link ImportBuilder} is created. The created instance is persisted to the
 * project configuration XML by using XStream, so this allows you to use
 * instance fields (like {@link name}) to remember the configuration.
 *
 * <p>
 * When a build is performed, the {@link perform(AbstractBuild, Launcher,
 * BuildListener)} method will be invoked.
 *
 */
public class ImportBuilder extends Builder {
    public static final GlobalConfig.GlobalConfigDescriptor GLOBALDESCRIPTOR = GlobalConfig.getGlobalConfigDescriptor();

    private String siteName;
    private String component;
    private String properties;

    /**
     * Constructor used for data-binding fields from the corresponding
     * config.jelly
     *
     * @param siteName The profile name of the UrbanDeploy site
     * @param component The name of the component on the UCD server
     * @param properties Any properties to create on the new version
     */
    @DataBoundConstructor
    public ImportBuilder(
            String siteName,
            String component,
            String properties)
    {
        this.siteName = siteName;
        this.component = component;
        this.properties = properties;
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

    public String getProperties() {
        return properties;
    }

    public void setProperties(String properties) {
        this.properties = properties;
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
            throw new AbortException("Skip component version import in IBM UrbanCode Deploy "
                    + "- build failed or aborted.");
        }

        EnvVars envVars = build.getEnvironment(listener);
        UrbanDeploySite udSite = getSite();
        VersionHelper versionHelper = new VersionHelper(udSite.getUri(), udSite.getClient());

        String resolvedComponent = envVars.expand(component);
        String resolvedProperties = envVars.expand(properties);

        listener.getLogger().println("Running version import on component '" + resolvedComponent + "'");
        versionHelper.importVersions(resolvedComponent, resolvedProperties);
        listener.getLogger().println("Version import ran successfully.");

        return true;
    }

    /**
     * This class holds the metadata for the ImportBuilder and allows it's data
     * fields to persist
     *
     */
    @Extension
    public static class ImportBuilderDescriptor extends BuildStepDescriptor<Builder> {

        public ImportBuilderDescriptor() {
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
            return "/plugin/ibm-ucdeploy-publisher/help-importbuilder.html";
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
         * Bind ImportBuilder data fields to user defined values {@inheritDoc}
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
            return "UrbanCode Deploy - Import Component Versions";
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
