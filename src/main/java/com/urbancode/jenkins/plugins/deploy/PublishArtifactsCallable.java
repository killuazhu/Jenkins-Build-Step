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
import hudson.model.BuildListener;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;

import java.io.File;
import java.util.UUID;

/**
 * This class runs the code to publish artifacts to a component version.
 * The enclosed code must be Callable so it can be run on a remote node (channel)
 *
 */
public class PublishArtifactsCallable implements Callable<Boolean, Exception> {

    //**********************************************************************************************
    // CLASS
    //**********************************************************************************************
    private static final long serialVersionUID = 34598734957L;

    //**********************************************************************************************
    // INSTANCE
    //**********************************************************************************************
    final private VersionHelper versionHelper;
    final private String baseDir;
    final private String fileIncludePatterns;
    final private String fileExcludePatterns;
    final private String component;
    final private String version;
    final private String description;
    final private String versionType;
    final private BuildListener listener;

    /**
     * Construct a Callable task
     * @param versionHelper The helper object to run UCD rest commands
     * @param baseDir The base directory to pull artifacts from
     * @param fileIncludePatterns
     * @param fileExcludePatterns
     * @param component
     * @param version
     * @param description
     * @param versionType Specifies an incremental or full version
     * @param listener Object to receive events that happen during a build
     */
    public PublishArtifactsCallable(
        VersionHelper versionHelper,
        String baseDir,
        String fileIncludePatterns,
        String fileExcludePatterns,
        String component,
        String version,
        String description,
        String versionType,
        BuildListener listener)
    {
        if (fileIncludePatterns == null) {
            fileIncludePatterns = "";
        }
        if (fileExcludePatterns == null) {
            fileExcludePatterns = "";
        }

        this.versionHelper = versionHelper;
        this.baseDir = baseDir;
        this.fileIncludePatterns = fileIncludePatterns;
        this.fileExcludePatterns = fileExcludePatterns;
        this.component = component;
        this.version = version;
        this.description = description;
        this.versionType = versionType;
        this.listener = listener;
    }

    /**
     * Call task on remote node, otherwise call would default to master node
     * @param channel The name of the node to call a task on
     * @throws ComponentVersionException
     */
    public void callOnChannel(VirtualChannel channel) throws AbortException {
        try {
            channel.call(this);
        }
        catch (Exception ex) {
            throw new AbortException("Failed to run build on channel: "
                    + channel + " : " + ex.getMessage());
        }
    }

    /**
     * Run this callable task on the defined channel
     * @return A boolean to represent the task success
     * @throws ComponentVersionException
     */
    public Boolean call() throws AbortException {
        File workDir = new File(baseDir);

        if (!workDir.exists()) {
            throw new AbortException("Base artifact directory " + workDir.getAbsolutePath() + " does not exist!");
        }

        listener.getLogger().println("Creating new version: " + version + " on component: " + component);

        UUID versionId = versionHelper.createComponentVersion(version, component, description);

        listener.getLogger().println("Successfully created new component version.");

        listener.getLogger().println("Working Directory: " + workDir.getPath());
        listener.getLogger().println("Includes: " + fileIncludePatterns);
        listener.getLogger().println("Excludes: " + fileExcludePatterns);

        listener.getLogger().println("Adding files to component version.");

        try {
            versionHelper.uploadVersionFiles(workDir, component, version, fileIncludePatterns, fileExcludePatterns);
            listener.getLogger().println("Successfully uploaded files to version.");
        }
        catch (AbortException ex) {
            try {
                listener.error("Deleting component version: " + versionId + " due to failed artifact upload.");
                versionHelper.deleteComponentVersion(versionId);
            }
            catch (AbortException e) {
                listener.error("Failed to delete component version :" + e.getMessage());
            }

            throw ex;
        }

        return true;
    }
}