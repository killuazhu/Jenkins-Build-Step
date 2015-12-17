package com.urbancode.ds.jenkins.plugins.urbandeploypublisher;

import hudson.model.BuildListener;
import hudson.remoting.Callable;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.ws.rs.core.UriBuilder;

import org.codehaus.jettison.json.JSONObject;

import com.ibm.uclab.csrepl.client.CodestationClient;
import com.ibm.uclab.csrepl.client.ops.Upload;
import com.ibm.uclab.csrepl.http.HttpClientWrapper;
import com.urbancode.commons.httpcomponentsutil.HttpClientBuilder;

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
    final private List<String> resolvedComponents;
    final private String resolvedVersionName;
    final private String resolvedDescription;
    final private String versionType;
    final private BuildListener listener;

    public PublishArtifactsCallable(
        String resolvedBaseDir,
        String resolvedDirectoryOffset,
        UrbanDeploySite udSite,
        String resolvedFileIncludePatterns,
        String resolvedFileExcludePatterns,
        List<String> resolvedComponents,
        String resolvedVersionName,
        String resolvedDescription,
        String versionType,
        BuildListener listener)
    {
        if (resolvedFileIncludePatterns == null) {
            resolvedFileIncludePatterns = "";
        }
        if (resolvedFileExcludePatterns == null) {
            resolvedFileExcludePatterns = "";
        }

        this.resolvedBaseDir = resolvedBaseDir;
        this.resolvedDirectoryOffset = resolvedDirectoryOffset;
        this.udSite = udSite;
        this.resolvedFileIncludePatterns = resolvedFileIncludePatterns;
        this.resolvedFileExcludePatterns = resolvedFileExcludePatterns;
        this.resolvedComponents = resolvedComponents;
        this.resolvedVersionName = resolvedVersionName;
        this.resolvedDescription = resolvedDescription;
        this.versionType = versionType;
        this.listener = listener;
    }

    public Boolean call() throws Exception {
        File workDir = new File(resolvedBaseDir);
        if (!workDir.exists()) {
            throw new Exception(
                "Base artifact directory " + workDir.getAbsolutePath() + " does not exist!");
        }
        if (resolvedDirectoryOffset != null && resolvedDirectoryOffset.trim().length() > 0) {
            workDir = new File(workDir, resolvedDirectoryOffset.trim());
        }

        Set<String> includes = new HashSet<String>();
        Set<String> excludes = new HashSet<String>();
        Map<String, String> includeMappings = new HashMap<String, String>();
        Map<String, String> excludeMappings = new HashMap<String, String>();

        for (String pattern : resolvedFileIncludePatterns.split("\n")) {
            boolean mapping = false;
            String component = null;

            if (pattern.contains("=")) {
                mapping = true;
                String[] componentPattern = pattern.split("=");

                if (componentPattern.length == 2) {
                    pattern = componentPattern[0];
                    component = componentPattern[1];
                }
                else {
                    throw new Exception("Invalid component mapping: " + componentPattern);
                }
            }
            else {
                mapping = false;
            }

            if (pattern != null && pattern.trim().length() > 0) {
                //configure component mappings
                if (mapping) {
                     if (component != null && component.trim().length() > 0) {
                         includeMappings.put(pattern.trim(), component.trim());
                     }
                }
                //no mapping specified on this line
                else {
                    includes.add(pattern.trim());
                }
            }
        }

        for (String pattern : resolvedFileExcludePatterns.split("\n")) {
            boolean mapping = false;
            String component = null;

            if (pattern.contains("=")) {
                mapping = true;
                String[] componentPattern = pattern.split("=");

                if (componentPattern.length == 2) {
                    pattern = componentPattern[0];
                    component = componentPattern[1];
                }
                else {
                    throw new Exception("Invalid component mapping: " + componentPattern);
                }
            }
            else {
                mapping = false;
            }

            if (pattern != null && pattern.trim().length() > 0) {
                //configure component mappings
                if (mapping) {
                     if (component != null && component.trim().length() > 0) {
                         excludeMappings.put(pattern.trim(), component.trim());
                     }
                }
                //no mapping specified on this line
                else {
                    excludes.add(pattern.trim());
                }
            }
        }

        listener.getLogger().println("Connecting to " + udSite.getUrl());
        boolean ok = false;

        for (String resolvedComponent : resolvedComponents) {
            UUID artifactSetId = createComponentVersion(resolvedComponent);
            try {
                listener.getLogger().println("Working Directory: " + workDir.getPath());

                HttpClientBuilder builder = new HttpClientBuilder();
                builder.setUsername(udSite.getUser());
                builder.setPassword(udSite.getPassword().toString());
                builder.setTrustAllCerts(true);
                builder.setPreemptiveAuthentication(true);
                HttpClientWrapper wrapper = new HttpClientWrapper(builder.buildClient());
                wrapper.setTimeout(60 * 1000, 5 * 60 * 1000);
                CodestationClient client = new CodestationClient(udSite.getUrl(), wrapper);
                List<String> includeList = new ArrayList<String>(includes);
                List<String> excludeList = new ArrayList<String>(excludes);

                //include mapped patterns
                for (Map.Entry<String, String> includeEntry : includeMappings.entrySet()) {
                    if (includeEntry.getValue().equals(resolvedComponent)) {
                        includeList.add(includeEntry.getKey());
                    }
                }

                //exclude mapped patterns
                for (Map.Entry<String, String> excludeEntry : excludeMappings.entrySet()) {
                    if (excludeEntry.getValue().equals(resolvedComponent)) {
                        excludeList.add(excludeEntry.getKey());
                    }
                }

                listener.getLogger().println("Includes: ");

                for (String include : includeList) {
                    listener.getLogger().println(include);
                }

                listener.getLogger().println("Excludes: ");

                for (String exclude : excludeList) {
                    listener.getLogger().println(excludes);
                }

                client.start();
                try {
                    Upload up = new Upload(client, workDir, artifactSetId);
                    up.setMessageStream(listener.getLogger());
                    up.setIncludes(includeList);
                    up.setExcludes(excludeList);
                    up.setSaveExecuteBits(true);
                    try {
                        up.run();
                    }
                    catch (Throwable e) {
                        throw new Exception("Failed to upload files", e);
                    }
                }
                finally {
                    client.stop();
                }
                ok = true;
            }
            finally {
                if (!ok) {
                    try {
                        deleteComponentVersion(artifactSetId);
                    }
                    catch (Throwable t) {
                        listener.getLogger().println("Deletion failed");
                        t.printStackTrace(listener.getLogger());
                    }
                }
            }
        }

        return true;
    }

    private UUID createComponentVersion(String resolvedComponent)
    throws Exception {
        if (resolvedVersionName == null || resolvedVersionName.isEmpty() || resolvedVersionName.length() > 255) {
            throw new Exception(String.format("Could not create version '%s' in UrbanCode Deploy. "
                    + "UrbanCode Deploy version names' length must be between 1 and  255 characters "
                    + "long. (Current length: %s)",
                    resolvedVersionName,
                    resolvedVersionName.length()));
        }
        UriBuilder uriBuilder = UriBuilder.fromPath(udSite.getUrl()).path("cli").path("version")
                        .path("createVersion");

        uriBuilder.queryParam("component", resolvedComponent);
        uriBuilder.queryParam("name", resolvedVersionName);
        uriBuilder.queryParam("description", resolvedDescription);
        uriBuilder.queryParam("type", versionType);
        URI uri = uriBuilder.build();

        listener.getLogger().println("Creating new version \""+resolvedVersionName+
                "\" on component "+resolvedComponent+"...");
        JSONObject jversion = new JSONObject(udSite.executeJSONPost(uri));
        UUID id = UUID.fromString(jversion.getString("id"));
        listener.getLogger().println("Successfully created new component version.");
        return id;
    }

    private void deleteComponentVersion(UUID id)
    throws Exception {
        UriBuilder uriBuilder = UriBuilder.fromPath(udSite.getUrl())
            .path("rest")
            .path("deploy")
            .path("version")
            .path(id.toString());
        URI uri = uriBuilder.build();
        listener.getLogger().println("Deleting version " + id + "...");
        udSite.executeJSONDelete(uri);
        listener.getLogger().println("Successfully deleted version.");
    }
}
