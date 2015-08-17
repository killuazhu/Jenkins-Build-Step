package com.urbancode.ds.jenkins.plugins.urbandeploypublisher;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.Hudson;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;

import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.core.UriBuilder;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
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
    private Boolean useAnotherUser = false;
    private String anotherUser;
    private String anotherPassword;
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
    private String deploymentResult;

    /**
     * Default constructor
     */
    @DataBoundConstructor
    public UrbanDeployPublisher(String siteName, Boolean useAnotherUser, String anotherUser, String anotherPassword, String component, String versionName, String directoryOffset, String baseDir,
                                String fileIncludePatterns, String fileExcludePatterns, Boolean skip, Boolean deploy,
                                String deployApp, String deployEnv, String deployProc, String properties, String description) {
        this.useAnotherUser = useAnotherUser;
        this.anotherUser = anotherUser;
        this.anotherPassword = anotherPassword;
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

    public void setUseAnotherUser(boolean useAnotherUser) {
        this.useAnotherUser = useAnotherUser;
    }

    public boolean isUseAnotherUser() {
        // For compatibility with old projects
        if (useAnotherUser == null) {
            useAnotherUser = false;
        }

        return useAnotherUser;
    }

    public String getAnotherUser() {
        return anotherUser;
    }

    public void setAnotherUser(String anotherUser) {
        this.anotherUser = anotherUser;
    }

    public String getAnotherPassword() {
        return anotherPassword;
    }

    public void setAnotherPassword(String anotherPassword) {
        this.anotherPassword = anotherPassword;
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
        if (build.getResult() == Result.FAILURE || build.getResult() == Result.ABORTED) {
            listener.getLogger().println("Skip artifacts upload and deployment to IBM UrbanCode Deploy - build failed or aborted.");
        }else{
            envMap = build.getEnvironment(listener);
            UrbanDeploySite udSite = getSite();

            String resolvedAnotherUser = null;
            String resolvedAnotherPassword = null;

            String resolvedComponent = null;
            String resolvedBaseDir = null;
            String resolvedVersionName = null;
            String resolvedFileIncludePatterns = null;
            String resolvedFileExcludePatterns = null;
            String resolvedDirectoryOffset = null;
            String resolvedProperties = null;
            String resolvedDescription = null;

            String resolvedDeployApp = null;
            String resolvedDeployEnv = null;
            String resolvedDeployProc = null;

            if(isUseAnotherUser()) {
                resolvedAnotherUser = resolveVariables(getAnotherUser());
                resolvedAnotherPassword = resolveVariables(getAnotherPassword());
                udSite = new UrbanDeploySite(udSite.getProfileName(), udSite.getUrl(), resolvedAnotherUser, resolvedAnotherPassword);
                listener.getLogger().println("Use different user to access IBM UrbanCode Deploy server: " + udSite.getUser() );
            }

            if (isSkip()) {
                listener.getLogger().println("Skip artifacts upload to IBM UrbanCode Deploy - step disabled.");
            }else{
                resolvedComponent = resolveVariables(getComponent());
                resolvedBaseDir = resolveVariables(getBaseDir());
                resolvedVersionName = resolveVariables(getVersionName());
                resolvedFileIncludePatterns = resolveVariables(fileIncludePatterns);
                resolvedFileExcludePatterns = resolveVariables(fileExcludePatterns);
                resolvedDirectoryOffset = resolveVariables(directoryOffset);
                resolvedProperties = resolveVariables(getProperties());
                resolvedDescription = resolveVariables(getDescription());
                try {
                    PublishArtifactsCallable task = new PublishArtifactsCallable(resolvedBaseDir, resolvedDirectoryOffset,
                            udSite, resolvedFileIncludePatterns, resolvedFileExcludePatterns, resolvedComponent,
                            resolvedVersionName, resolvedDescription, listener);
                    launcher.getChannel().call(task);

                    PropsHelper propsHelper = new PropsHelper();
                    propsHelper.setComponentVersionProperties(udSite.getUrl(), resolvedComponent, resolvedVersionName,
                            resolvedProperties, udSite.getUser(), udSite.getPassword(), listener);
                    String linkName = "Jenkins Job " + build.getDisplayName();
                    String linkUrl = Hudson.getInstance().getRootUrl() + build.getUrl();
                    listener.getLogger().println("Add Jenkins job link " + linkUrl );
                    this.addLinkToComp(udSite, resolvedComponent, resolvedVersionName, linkName, linkUrl);
                }
                catch (Throwable th) {
                    th.printStackTrace(listener.error("Failed to upload files" + th));
                    build.setResult(Result.UNSTABLE);
                    return true;
                }
            }

            if (isDeploy()){
                resolvedDeployApp = resolveVariables(getDeployApp());
                resolvedDeployEnv = resolveVariables(getDeployEnv());
                resolvedDeployProc = resolveVariables(getDeployProc());
                try {
                    if (resolvedDeployApp == null || resolvedDeployApp.trim().length() == 0) {
                          throw new Exception("Deploy Application is a required field if Deploy is selected!");
                      }
                      if (resolvedDeployEnv == null || resolvedDeployEnv.trim().length() == 0) {
                          throw new Exception("Deploy Environment is a required field if Deploy is selected!");
                      }
                      if (resolvedDeployProc == null || resolvedDeployProc.trim().length() == 0) {
                          throw new Exception("Deploy Process is a required field if Deploy is selected!");
                      }

                      listener.getLogger().println("Starting deployment process " + resolvedDeployProc + " of application " +
                              resolvedDeployApp + " in environment " + resolvedDeployEnv);
                      String requestId = null;

                      requestId = createDefaultProcessRequest(udSite, resolvedDeployApp, resolvedDeployEnv,
                                      resolvedDeployProc, resolvedComponent, resolvedVersionName, listener);

                      listener.getLogger().println("Deployment request id is: " + requestId);
                      if(requestId.contains("requestId")){
                          requestId = requestId.substring(requestId.indexOf("\"") + 12).trim();
                          requestId = requestId.substring(requestId.indexOf("\"")+1, requestId.lastIndexOf("\""));
                      }
                      listener.getLogger().println("Deployment of application request " + requestId + " of application " +
                              resolvedDeployApp + " is running...... ");
                      long startTime = new Date().getTime();
                      while(!checkDeploymentProcessStatus(udSite, requestId)){
                          Thread.sleep(3000);
                      }
                      if(deploymentResult != null){
                          long duration = (new Date().getTime()-startTime)/1000 ; 
                          listener.getLogger().println("Finished deployment of application request " + requestId +
                                  " for application " + resolvedDeployApp + " in environment " +
                                  resolvedDeployEnv + " in " + duration + " seconds");
                          listener.getLogger().println("The deployment " + deploymentResult + 
                                  ". See the UrbanCode Deploy deployment logs for details.");
                          if ("faulted".equalsIgnoreCase(deploymentResult) || 
                                  "failed to start".equalsIgnoreCase(deploymentResult)) {
                              build.setResult(Result.UNSTABLE);
                          }
                      }
                }
                catch (Throwable th) {
                    th.printStackTrace(listener.error("Failed to deploy application" + th));
                    build.setResult(Result.UNSTABLE);
                }
            }
            else {
                listener.getLogger().println("Skip deploy application to IBM UrbanCode Deploy - step disabled.");
            }
        }
        return true;
    }

    private boolean checkDeploymentProcessStatus(UrbanDeploySite site, String proc) throws Exception {
        boolean processFinished = false;
        URI uri = UriBuilder.fromPath(site.getUrl()).path("cli").path("applicationProcessRequest")
                .path("requestStatus").queryParam("request", proc).build();
        String requestStatusResult = site.executeJSONGet(uri);
        if (requestStatusResult != null) {
            JSONObject resultJSON = new JSONObject(requestStatusResult);
            String executionStatus = resultJSON.optString("status");
            String executionResult = resultJSON.optString("result");
            if (executionStatus == null || "".equals(executionStatus)) {
                deploymentResult = "FAULTED";
                processFinished = true;
            }
            else if ("closed".equalsIgnoreCase(executionStatus) ||
                "faulted".equalsIgnoreCase(executionStatus) ||
                "faulted".equalsIgnoreCase(executionResult)) {
                deploymentResult = executionResult;
                processFinished = true;
            }
        }
        return processFinished;
    }

    /**
     * This method will trigger application deployment process with latest versions of each component.
     */
    private String createDefaultProcessRequest(UrbanDeploySite site, String app, String env, String proc,
                    String componentName, String versionName, BuildListener listener)
            throws Exception {
        URI uri = UriBuilder.fromPath(site.getUrl()).path("cli").path("applicationProcessRequest")
                .path("request").build();
        JSONObject appProcess = new JSONObject();
        appProcess.put("application", app);
        appProcess.put("applicationProcess", proc);
        appProcess.put("environment", env);

        JSONArray compsArray = new JSONArray();
        JSONObject compObj = new JSONObject();
        compObj.put("version", versionName);
        compObj.put("component", componentName);
        compsArray.put(compObj);
        appProcess.put("versions", compsArray);
        String appProcessStr = appProcess.toString();
        listener.getLogger().println("Application process deployment request: "+appProcessStr);
        String result = site.executeJSONPut(uri,appProcessStr);
        listener.getLogger().println("Application process deployment result: "+result);
        return result;

    }

    private boolean addLinkToComp(UrbanDeploySite site, String compName, String versionName, String linkName, String linkUrl)
            throws Exception{
        URI uri = UriBuilder.fromPath(site.getUrl()).path("cli").path("version")
                .path("addLink").queryParam("component", compName).queryParam("version",versionName).
                queryParam("linkName", linkName).queryParam("link", linkUrl).build();
        String result = site.executeJSONPut(uri,"");
        return (result != null && result.toLowerCase().indexOf("succeeded") != -1);
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
