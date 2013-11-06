package com.urbancode.ds.jenkins.plugins.urbandeploypublisher;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.core.UriBuilder;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * <p> This class implements the UrbanDeploy publisher process by using the {@link
 * com.urbancode.ds.jenkins.plugins.urbandeploypublisher.UrbanDeploySite}. </p>
 */
public class UrbanDeployPublisher extends Notifier {

    /**
     * Hold an instance of the Descriptor implementation for the UrbanDeploy Publisher.
     */
    @Extension
    public static final UrbanDeployPublisherDescriptor DESCRIPTOR = new UrbanDeployPublisherDescriptor();

    private String siteName;
    private String component;
    private String baseDir;
    private String directoryOffset;
    private String fileIncludePatterns;
    private String fileExcludePatterns;
    private String versionName;
    private Boolean skip = false;
    private Boolean deploy = false;
    private String deployApp;
    private String deployEnv;
    private String deployProc;
    private Map<String, String> envMap = null;
    
    private String properties;
    private String description;

    /**
     * Default constructor
     */
    @DataBoundConstructor
    public UrbanDeployPublisher(String siteName, String component, String versionName, String directoryOffset, String baseDir,
                                String fileIncludePatterns, String fileExcludePatterns, Boolean skip, Boolean deploy,
                                String deployApp, String deployEnv, String deployProc, String properties, String description) {
        this.component = component;
        this.versionName = versionName;
        this.baseDir = baseDir;
        this.directoryOffset = directoryOffset;
        this.fileIncludePatterns = fileIncludePatterns;
        this.fileExcludePatterns = fileExcludePatterns;
        this.siteName = siteName;
        this.skip = skip;
        this.deploy = deploy;
        this.deployApp = deployApp;
        this.deployEnv = deployEnv;
        this.deployProc = deployProc;
        this.properties = properties;
        this.description = description;
    }

    public String getSiteName() {
        String name = siteName;
        if (name == null) {
            UrbanDeploySite[] sites = DESCRIPTOR.getSites();
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

    public String getBaseDir() {
        return baseDir;
    }

    public void setBaseDir(String baseDir) {
        this.baseDir= baseDir;
    }

    public String getDirectoryOffset() {
        return directoryOffset;
    }

    public void setDirectoryOffset(String directoryOffset) {
        this.directoryOffset = directoryOffset;
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

    public String getVersionName() {
        return versionName;
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getProperties() {
        return properties;
    }
    
    public void setProperties(String properties) {
        this.properties = properties;
    }

    public void setSkip(boolean skip) {
        this.skip = skip;
    }

    public boolean isSkip() {
        return skip;
    }

    public void setDeploy(boolean deploy) {
        this.deploy = deploy;
    }

    public boolean isDeploy() {
        return deploy;
    }

    public void setDeployApp(String deployApp) {
        this.deployApp = deployApp;
    }

    public String getDeployApp() {
        return deployApp;
    }

    public void setDeployEnv(String deployEnv) {
        this.deployEnv = deployEnv;
    }

    public String getDeployEnv() {
        return deployEnv;
    }

    public void setDeployProc(String deployProc) {
        this.deployProc = deployProc;
    }

    public String getDeployProc() {
        return deployProc;
    }

    /**
     * This method returns the configured UrbanDeploySite object which match the siteName of the UrbanDeployPublisher
     * instance. (see Manage Hudson and System Configuration point UrbanDeploy)
     *
     * @return the matching UrbanDeploySite or null
     */
    public UrbanDeploySite getSite() {
        UrbanDeploySite[] sites = DESCRIPTOR.getSites();
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


    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    /**
     * {@inheritDoc}
     *
     * @param build
     * @param launcher
     * @param listener
     * @return
     * @throws InterruptedException
     * @throws java.io.IOException  {@inheritDoc}
     * @see hudson.tasks.BuildStep#perform(hudson.model.Build, hudson.Launcher, hudson.model.BuildListener)
     */

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {
        if (isSkip()) {
            listener.getLogger().println("Skip artifacts upload to IBM UrbanCode Deploy - step disabled.");
        }
        else if (build.getResult() == Result.FAILURE || build.getResult() == Result.ABORTED) {
            listener.getLogger().println("Skip artifacts upload to IBM UrbanCode Deploy - build failed or aborted.");
        }
        else {
            envMap = build.getEnvironment(listener);
            
            String resolvedComponent = resolveVariables(getComponent());
            String resolvedBaseDir = resolveVariables(getBaseDir());
            String resolvedVersionName = resolveVariables(getVersionName());
            String resolvedFileIncludePatterns = resolveVariables(fileIncludePatterns);
            String resolvedFileExcludePatterns = resolveVariables(fileExcludePatterns);
            String resolvedDirectoryOffset = resolveVariables(directoryOffset);
            String resolvedDeployApp = resolveVariables(getDeployApp());
            String resolvedDeployEnv = resolveVariables(getDeployEnv());
            String resolvedDeployProc = resolveVariables(getDeployProc());
            String resolvedProperties = resolveVariables(getProperties());
            String resolvedDescription = resolveVariables(getDescription());

            UrbanDeploySite udSite = getSite();
            try {
                PublishArtifactsCallable task = new PublishArtifactsCallable(resolvedBaseDir, resolvedDirectoryOffset,
                        udSite, resolvedFileIncludePatterns, resolvedFileExcludePatterns, resolvedComponent,
                        resolvedVersionName, resolvedDescription, listener);
                launcher.getChannel().call(task);

                PropsHelper propsHelper = new PropsHelper();
                propsHelper.setComponentVersionProperties(udSite.getUrl(), resolvedComponent, resolvedVersionName, resolvedProperties, udSite.getUser(), udSite.getPassword(), listener);
                
                if (isDeploy()) {
                    if (resolvedDeployApp == null || resolvedDeployApp.trim().length() == 0) {
                        throw new Exception("Deploy Application is a required field if Deploy is selected!");
                    }
                    if (resolvedDeployEnv == null || resolvedDeployEnv.trim().length() == 0) {
                        throw new Exception("Deploy Environment is a required field if Deploy is selected!");
                    }
                    if (resolvedDeployProc == null || resolvedDeployProc.trim().length() == 0) {
                        throw new Exception("Deploy Process is a required field if Deploy is selected!");
                    }

                    listener.getLogger().println("Starting deployment of " + resolvedDeployApp + " in " + resolvedDeployEnv);
                    createProcessRequest(udSite, resolvedComponent, resolvedVersionName, 
                            resolvedDeployApp, resolvedDeployEnv, resolvedDeployProc);
                }
            }
            catch (Throwable th) {
                th.printStackTrace(listener.error("Failed to upload files" + th));
                build.setResult(Result.UNSTABLE);
            }
        }

        return true;
    }

    private void createProcessRequest(UrbanDeploySite site, String componentName, String versionName,
            String app, String env, String proc)
            throws Exception {
        URI uri = UriBuilder.fromPath(site.getUrl()).path("cli").path("applicationProcessRequest")
                .path("request").build();
        String json =
                "{\"application\":\"" + app +
                "\",\"applicationProcess\":\"" + proc +
                "\",\"environment\":\"" + env +
                "\",\"versions\":[{\"version\":\"" + versionName +
                "\",\"component\":\"" + componentName + "\"}]}";
        site.executeJSONPut(uri,json);

    }

    private String resolveVariables(String input) {
        String result = input;
        if (input != null && input.trim().length() > 0) {
            Pattern pattern = Pattern.compile("\\$\\{[^}]*}");
            Matcher matcher = pattern.matcher(result);
            while (matcher.find()) {
                String key = result.substring(matcher.start() + 2, matcher.end() - 1);
                if (envMap.containsKey(key)) {
                    result = matcher.replaceFirst(Matcher.quoteReplacement(envMap.get(key)));
                    matcher.reset(result);
                }
            }
        }

        return result;
    }
}


