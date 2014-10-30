package com.urbancode.ds.jenkins.plugins.urbandeploypublisher;

import hudson.model.BuildListener;
import hudson.remoting.Callable;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
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
    final private String resolvedComponent;
    final private String resolvedVersionName;
    final private String resolvedDescription;
    final private BuildListener listener;

    public PublishArtifactsCallable(
        String resolvedBaseDir,
        String resolvedDirectoryOffset,
        UrbanDeploySite udSite,
        String resolvedFileIncludePatterns,
        String resolvedFileExcludePatterns,
        String resolvedComponent,
        String resolvedVersionName,
        String resolvedDescription,
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
        this.resolvedComponent = resolvedComponent;
        this.resolvedVersionName = resolvedVersionName;
        this.resolvedDescription = resolvedDescription;
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
        for (String pattern : resolvedFileIncludePatterns.split("\n")) {
            if (pattern != null && pattern.trim().length() > 0) {
                includes.add(pattern.trim());
            }
        }
        for (String pattern : resolvedFileExcludePatterns.split("\n")) {
            if (pattern != null && pattern.trim().length() > 0) {
                excludes.add(pattern.trim());
            }
        }

        listener.getLogger().println("Connecting to " + udSite.getUrl());
        boolean ok = false;
        UUID artifactSetId = createComponentVersion();
        try {
            listener.getLogger().println("Working Directory: " + workDir.getPath());
            listener.getLogger().println("Includes: " + resolvedFileIncludePatterns);
            listener.getLogger().println("Excludes: " + resolvedFileExcludePatterns);

            HttpClientBuilder builder = new HttpClientBuilder();
            builder.setUsername(udSite.getUser());
            builder.setPassword(udSite.getPassword());
            builder.setTrustAllCerts(true);
            builder.setPreemptiveAuthentication(true);
            HttpClientWrapper wrapper = new HttpClientWrapper(builder.buildClient());
            wrapper.setTimeout(60 * 1000, 5 * 60 * 1000);
            CodestationClient client = new CodestationClient(udSite.getUrl(), wrapper);

            client.start();
            try {
                Upload up = new Upload(client, workDir, artifactSetId);
                up.setMessageStream(listener.getLogger());
                up.setIncludes(new ArrayList<String>(includes));
                up.setExcludes(new ArrayList<String>(excludes));
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

        return true;
    }

    private UUID createComponentVersion()
    throws Exception {
        UriBuilder uriBuilder = UriBuilder.fromPath(udSite.getUrl()).path("cli").path("version")
                        .path("createVersion");

        uriBuilder.queryParam("component", resolvedComponent);
        uriBuilder.queryParam("name", resolvedVersionName);
        uriBuilder.queryParam("description", resolvedDescription);
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
