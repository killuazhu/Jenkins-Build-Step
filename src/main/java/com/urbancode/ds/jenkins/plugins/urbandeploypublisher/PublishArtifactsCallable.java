package com.urbancode.ds.jenkins.plugins.urbandeploypublisher;

import hudson.model.BuildListener;
import hudson.remoting.Callable;

import java.io.File;
import java.net.URI;
import java.net.URLDecoder;
import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.UriBuilder;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;

import com.urbancode.commons.fileutils.filelister.FileListerBuilder;
import com.urbancode.vfs.client.Client;
import com.urbancode.vfs.common.ClientChangeSet;
import com.urbancode.vfs.common.ClientPathEntry;

public class PublishArtifactsCallable implements Callable<Boolean, Exception> {

    //**********************************************************************************************
    // CLASS
    //**********************************************************************************************
    private static final long serialVersionUID = 34598734957L;

    //**********************************************************************************************
    // INSTANCE
    //**********************************************************************************************
    final private String resolvedBaseDir;
    final private String resolvedDirectoryOffset;
    final private UrbanDeploySite udSite;
    final private String resolvedFileIncludePatterns;
    final private String resolvedFileExcludePatterns;
    final private String resolvedComponent;
    final private String resolvedVersionName;
    final private String resolvedDescription;
    final private BuildListener listener;
    
    public PublishArtifactsCallable(String resolvedBaseDir, String resolvedDirectoryOffset, UrbanDeploySite udSite,
            String resolvedFileIncludePatterns, String resolvedFileExcludePatterns, String resolvedComponent,
            String resolvedVersionName, String resolvedDescription, BuildListener listener) {
        this.resolvedBaseDir = resolvedBaseDir;
        this.resolvedDirectoryOffset = resolvedDirectoryOffset;
        this.udSite = udSite;
        this.resolvedFileIncludePatterns = resolvedFileIncludePatterns;
        this.resolvedFileExcludePatterns = resolvedFileExcludePatterns;
        this.resolvedComponent = resolvedComponent;
        this.resolvedVersionName = resolvedVersionName;
        this.resolvedDescription = resolvedDescription;
        this.listener = listener;
    }
    
    
    @Override
    public Boolean call() throws Exception {
        File workDir = new File(resolvedBaseDir);
        if (!workDir.exists()) throw new Exception("Base artifact directory " + workDir.toString()
                + " does not exist!");
        if (resolvedDirectoryOffset != null && resolvedDirectoryOffset.trim().length() > 0) {
            workDir = new File(workDir, resolvedDirectoryOffset.trim());
        }

        Set<String> includesSet = new HashSet<String>();
        Set<String> excludesSet = new HashSet<String>();
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
        createComponentVersion(udSite, resolvedComponent, resolvedVersionName, resolvedDescription, listener);
        listener.getLogger().println("Working Directory: " + workDir.getPath());
        listener.getLogger().println("Includes: " + resolvedFileIncludePatterns);
        listener.getLogger().println("Excludes: " + (resolvedFileExcludePatterns == null ? "" : resolvedFileExcludePatterns));

        Client client = null;
        String stageId = null;
        try {
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
                //staging directory get deleted in labelChangeSet call
                listener.getLogger().println("Deleted staging directory: " + stageId);
            }
            else {
                listener.getLogger().println("Did not find any files to upload!");
            }
        }
        catch (Throwable e) {
            if (client != null && stageId != null) {
                try {
                    client.deleteStagingDirectory(stageId);
                    listener.getLogger().println("Deleted staging directory: " + stageId);
                }
                catch (Exception ex) {
                    listener.getLogger()
                            .println("Failed to delete staging directory " + stageId + ": " + ex.getMessage());
                }
            }
            throw new Exception("Failed to upload files", e);
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

    private void createComponentVersion(UrbanDeploySite site, String componentName,
            String versionName, String description, BuildListener listener)
    throws Exception {
        UriBuilder uriBuilder = UriBuilder.fromPath(site.getUrl()).path("cli").path("version")
                        .path("createVersion");
        
        uriBuilder.queryParam("component", componentName);
        uriBuilder.queryParam("name", versionName);
        uriBuilder.queryParam("description", description);
        URI uri = uriBuilder.build();
        
        listener.getLogger().println("Creating new version \""+versionName+
                "\" on component "+componentName+"...");
        site.executeJSONPost(uri);
        listener.getLogger().println("Successfully created new component version.");
    }
}
