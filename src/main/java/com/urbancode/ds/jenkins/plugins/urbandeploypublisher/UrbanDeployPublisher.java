package com.urbancode.ds.jenkins.plugins.urbandeploypublisher;

import com.urbancode.commons.fileutils.filelister.FileListerBuilder;
import com.urbancode.vfs.client.Client;
import com.urbancode.vfs.common.ClientChangeSet;
import com.urbancode.vfs.common.ClientPathEntry;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import org.apache.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.ws.rs.core.UriBuilder;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /**
     * Default constructor
     */
    @DataBoundConstructor
    public UrbanDeployPublisher(String siteName, String component, String versionName, String directoryOffset, String baseDir,
                                String fileIncludePatterns, String fileExcludePatterns, Boolean skip, Boolean deploy,
                                String deployApp, String deployEnv, String deployProc) {
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
            listener.getLogger().println("Skip artifacts upload to UrbanDeploy - step disabled.");
        }
        else if (build.getResult() == Result.FAILURE || build.getResult() == Result.ABORTED) {
            listener.getLogger().println("Skip artifacts upload to UrbanDeploy - build failed or aborted.");
        }
        else {
            envMap = build.getEnvironment(listener);
            
            String resolvedComponent = resolveVariables(getComponent());
            String resolvedBaseDir = resolveVariables(getBaseDir());
            String resolvedVersionName = resolveVariables(getVersionName());
            String resolvedFileIncludePatterns = resolveVariables(fileIncludePatterns);
            String resolvedFileExcludePatterns = resolveVariables(fileExcludePatterns);
            String resolvedDirectoryOffset = resolveVariables(directoryOffset);

            UrbanDeploySite udSite = null;
            Client client = null;
            String stageId = null;
            try {
                File workDir = new File(resolvedBaseDir);
                if (!workDir.exists()) throw new Exception("Base artifact directory " + workDir.toString()
                        + " does not exist!");
                if (resolvedDirectoryOffset != null && resolvedDirectoryOffset.trim().length() > 0) {
                    workDir = new File(workDir, resolvedDirectoryOffset.trim());
                }

                udSite = getSite();
                Set includesSet = new HashSet();
                Set excludesSet = new HashSet();
                for (String pattern : resolvedFileIncludePatterns.split("\n")) {
                    if (pattern != null && pattern.trim().length() > 0) {
                        includesSet.add(pattern.trim());
                    }
                }
                if (resolvedFileExcludePatterns != null) {
                    for (String pattern : resolvedFileExcludePatterns.split("\n")) {
                        if (pattern != null && pattern.trim().length() > 0) {
                            excludesSet.add(pattern.trim());
                        }
                    }
                }

                String[] includesArray = new String[includesSet.size()];
                includesArray = (String[]) includesSet.toArray(includesArray);

                String[] excludesArray = new String[excludesSet.size()];
                excludesArray = (String[]) excludesSet.toArray(excludesArray);


                listener.getLogger().println("Connecting to " + udSite.getUrl());
                listener.getLogger()
                        .println("Creating new component version " + resolvedVersionName + " for component " + resolvedComponent);
                createComponentVersion(udSite, resolvedComponent, resolvedVersionName);
                listener.getLogger().println("Working Directory: " + workDir.getPath());
                listener.getLogger().println("Includes: " + resolvedFileIncludePatterns);
                listener.getLogger().println("Excludes: " + (resolvedFileExcludePatterns == null ? "" : resolvedFileExcludePatterns));

                ClientPathEntry[] entries = ClientPathEntry
                        .createPathEntriesFromFileSystem(workDir, includesArray, excludesArray,
                                FileListerBuilder.Directories.INCLUDE_ALL, FileListerBuilder.Permissions.BEST_EFFORT,
                                FileListerBuilder.Symlinks.AS_LINK, "SHA-256");

                listener.getLogger().println("Invoke vfs client...");
                client = new Client(udSite.getUrl() + "/vfs", null, null);
                stageId = client.createStagingDirectory();
                listener.getLogger().println("Created staging directory: " + stageId);

                if (entries.length > 0) {

                    for (ClientPathEntry entry : entries) {
                        File entryFile = new File(workDir, entry.getPath());
                        listener.getLogger().println("Adding " + entry.getPath() + " to staging directory...");
                        client.addFileToStagingDirectory(stageId, entry.getPath(), entryFile);
                    }

                    String repositoryId = getComponentRepositoryId(udSite, resolvedComponent);
                    ClientChangeSet changeSet =
                            ClientChangeSet.newChangeSet(repositoryId, udSite.getUser(), "Uploaded by Jenkins", entries);

                    listener.getLogger().println("Committing change set...");
                    String changeSetId = client.commitStagingDirectory(stageId, changeSet);
                    listener.getLogger().println("Created change set: " + changeSetId);

                    listener.getLogger().println("Labeling change set with label: " + resolvedVersionName);
                    client.labelChangeSet(repositoryId, URLDecoder.decode(changeSetId, "UTF-8"), resolvedVersionName,
                    udSite.getUser(), "Associated with version " + resolvedVersionName);
                    listener.getLogger().println("Done labeling change set!");
                }
                else {
                    listener.getLogger().println("Did not find any files to upload!");
                }

                if (isDeploy()) {
                    if (getDeployApp() == null || getDeployApp().trim().length() == 0) {
                        throw new Exception("Deploy Application is a required field if Deploy is selected!");
                    }
                    if (getDeployEnv() == null || getDeployEnv().trim().length() == 0) {
                        throw new Exception("Deploy Environment is a required field if Deploy is selected!");
                    }
                    if (getDeployProc() == null || getDeployProc().trim().length() == 0) {
                        throw new Exception("Deploy Process is a required field if Deploy is selected!");
                    }

                    listener.getLogger().println("Starting deployment of " + getDeployApp() + " in " + getDeployEnv());
                    createProcessRequest(udSite, resolvedComponent, resolvedVersionName);
                }

            }
            catch (Throwable th) {
                th.printStackTrace(listener.error("Failed to upload files" + th));
                build.setResult(Result.UNSTABLE);
            }
            finally {
                if (client != null && stageId != null) {
                    try {
                        client.deleteStagingDirectory(stageId);
                        listener.getLogger().println("Deleted staging directory: " + stageId);
                    }
                    catch (Exception e) {
                        listener.getLogger()
                                .println("Failed to delete staging directory " + stageId + ": " + e.getMessage());
                    }
                }
            }
        }

        return true;
    }

    private String getComponentRepositoryId(UrbanDeploySite site, String componentName)
            throws Exception {
        String result = null;
        URI uri = UriBuilder.fromPath(site.getUrl()).path("rest").path("deploy").path("component").path(componentName)
                .build();

        String componentContent = site.executeJSONGet(uri);

        JSONArray properties = new JSONObject(componentContent).getJSONArray("properties");
        if (properties != null) {
            for (int i = 0; i < properties.length(); i++) {
                JSONObject propertyJson = properties.getJSONObject(i);
                String propName = propertyJson.getString("name");
                String propValue = propertyJson.getString("value");

                if ("code_station/repository".equalsIgnoreCase(propName)) {
                    result = propValue.trim();
                    break;
                }
            }
        }
        return result;
    }

    private void createComponentVersion(UrbanDeploySite site, String componentName, String versionName)
            throws Exception {
        URI uri = UriBuilder.fromPath(site.getUrl()).path("cli").path("version")
                .path("createVersion").build();
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("component", componentName);
        parameters.put("name", versionName);
        
        site.executeJSONPost(uri, parameters);
    }

    private void createProcessRequest(UrbanDeploySite site, String componentName, String versionName)
            throws Exception {
        URI uri = UriBuilder.fromPath(site.getUrl()).path("cli").path("applicationProcessRequest")
                .path("request").build();
        String json =
                "{\"application\":\"" + getDeployApp() +
                "\",\"applicationProcess\":\"" + getDeployProc() +
                "\",\"environment\":\"" + getDeployEnv() +
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


