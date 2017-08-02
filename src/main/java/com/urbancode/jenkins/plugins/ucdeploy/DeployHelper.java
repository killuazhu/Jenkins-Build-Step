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
import hudson.model.TaskListener;

import java.io.IOException;
import java.lang.InterruptedException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.http.impl.client.DefaultHttpClient;
import org.codehaus.jettison.json.JSONException;
import org.kohsuke.stapler.DataBoundConstructor;

import com.urbancode.jenkins.plugins.ucdeploy.ProcessHelper;
import com.urbancode.jenkins.plugins.ucdeploy.ProcessHelper.CreateProcessBlock;
import com.urbancode.ud.client.ApplicationClient;

/**
 * This class is used to provide access to the UrbanCode Deploy rest client
 * and run component version related rest calls
 *
 */
@SuppressWarnings("deprecation") // Triggered by DefaultHttpClient
public class DeployHelper {
    private ApplicationClient appClient;
    private TaskListener listener;
    private EnvVars envVars;
    private String description = "Requested from Jenkins";

    public DeployHelper(URI ucdUrl, DefaultHttpClient httpClient, TaskListener listener, EnvVars envVars) {
        appClient = new ApplicationClient(ucdUrl, httpClient);
        this.listener = listener;
        this.envVars = envVars;
    }

    public static class DeployBlock {
        private String deployApp;
        private String deployEnv;
        private String deployProc;
        private CreateProcessBlock createProcess;
        private CreateSnapshotBlock createSnapshot;
        private String deployVersions;
        private Boolean deployOnlyChanged;

        @DataBoundConstructor
        public DeployBlock(
            String deployApp,
            String deployEnv,
            String deployProc,
            CreateProcessBlock createProcess,
            CreateSnapshotBlock createSnapshot,
            String deployVersions,
            Boolean deployOnlyChanged)
        {
            this.deployApp = deployApp;
            this.deployEnv = deployEnv;
            this.deployProc = deployProc;
            this.createProcess = createProcess;
            this.createSnapshot = createSnapshot;
            this.deployVersions = deployVersions;
            this.deployOnlyChanged = deployOnlyChanged;
        }

        public String getDeployApp() {
            if (deployApp != null) {
                return deployApp;
            }
            else {
                return "";
            }
        }

        public String getDeployEnv() {
            if (deployEnv != null) {
                return deployEnv;
            }
            else {
                return "";
            }
        }

        public String getDeployProc() {
            if (deployProc != null) {
                return deployProc;
            }
            else {
                return "";
            }
        }

        public CreateProcessBlock getCreateProcessBlock() {
            return createProcess;
        }

        public Boolean createProcessChecked() {
            if (getCreateProcessBlock() == null) {
                return false;
            }
            else {
                return true;
            }
        }

        public CreateSnapshotBlock getCreateSnapshotBlock() {
            return createSnapshot;
        }

        public Boolean createSnapshotChecked() {
            if (getCreateSnapshotBlock() == null) {
                return false;
            }
            else {
                return true;
            }
        }

        public String getDeployVersions() {
            if (deployVersions != null) {
                return deployVersions;
            }
            else {
                return "";
            }
        }

        public Boolean getDeployOnlyChanged() {
            if (deployOnlyChanged != null) {
                return deployOnlyChanged;
            }
            else {
                return false;
            }
        }
    }

    public static class CreateSnapshotBlock {
        private String snapshotName;

        @DataBoundConstructor
        public CreateSnapshotBlock(String snapshotName) {
            this.snapshotName = snapshotName;
        }

        public String getSnapshotName() {
            return snapshotName;
        }
    }

    /**
     * Deploys a version in IBM UrbanCode Deploys
     *
     * @param deployBlock The DeployBlock containing the structure of the deployment
     * @throws AbortException
     */
    public void deployVersion(DeployBlock deployBlock) throws AbortException {
        String deployApp = envVars.expand(deployBlock.getDeployApp());
        String deployEnv = envVars.expand(deployBlock.getDeployEnv());
        String deployProc = envVars.expand(deployBlock.getDeployProc());
        String deployVersions = envVars.expand(deployBlock.getDeployVersions());

        // create process
        if (deployBlock.createProcessChecked()) {
            ProcessHelper processHelper = new ProcessHelper(appClient, listener, envVars);
            processHelper.createProcess(deployApp, deployProc, deployBlock.getCreateProcessBlock());
        }

        // required fields
        if (deployApp.isEmpty()) {
            throw new AbortException("Deploy Application is a required field for deployment.");
        }
        if (deployEnv.isEmpty()) {
            throw new AbortException("Deploy Environment is a required field for deployment.");
        }
        if (deployProc.isEmpty()) {
            throw new AbortException("Deploy Process is a required field for deployment.");
        }
        if (deployVersions.isEmpty()) {
            throw new AbortException("Deploy Versions is a required field for deployment.");
        }

        // deploy
        String snapshot = "";
        Map<String, List<String>> componentVersions = new HashMap<String, List<String>>();
        if (deployVersions.contains("=")) {
            if (deployVersions.contains("\n")) {
                throw new AbortException("Only a single SNAPSHOT can be specified");
            }
            snapshot = deployVersions;
            listener.getLogger().println("Deploying SNAPSHOT '" + snapshot + "'");
        }
        else {
            componentVersions = readComponentVersions(deployVersions);
            listener.getLogger().println("Deploying component versions '" + componentVersions + "'");
        }

        listener.getLogger().println("Starting deployment process '" + deployProc + "' of application '" + deployApp +
                                     "' in environment '" + deployEnv + "'");
        UUID appProcUUID;
        try {
            appProcUUID = appClient.requestApplicationProcess(deployApp,
                                                              deployProc,
                                                              description,
                                                              deployEnv,
                                                              snapshot,
                                                              deployBlock.getDeployOnlyChanged(),
                                                              componentVersions);

        }
        catch (IOException ex) {
            throw new AbortException("Could not request application process: " + ex.getMessage());
        }
        catch (JSONException ex) {
            throw new AbortException("An error occurred while processing the JSON object for the application process " +
                                     "request: " + ex.getMessage());
        }

        listener.getLogger().println("Deployment request id is: '" + appProcUUID.toString() + "'");
        listener.getLogger().println("Deployment is running. Waiting for UCD Server feedback.");

        long startTime = new Date().getTime();
        boolean processFinished = false;
        String deploymentResult = "";

        while (!processFinished) {
            deploymentResult = checkDeploymentProcessResult(appProcUUID.toString());

            if (!deploymentResult.isEmpty()
                    && !deploymentResult.equalsIgnoreCase("NONE")
                    && !deploymentResult.equalsIgnoreCase("SCHEDULED FOR FUTURE")) {
                processFinished = true;

                if (deploymentResult.equalsIgnoreCase("FAULTED") || deploymentResult.equalsIgnoreCase("FAILED TO START")) {
                    throw new AbortException("Deployment process failed with result " + deploymentResult);
                }
            }

            // give application process more time to complete
            try {
                Thread.sleep(3000);
            }
            catch (InterruptedException ex) {
                throw new AbortException("Could not wait to check deployment result: " + ex.getMessage());
            }
        }

        // create snapshot of environment
        if (deployBlock.createSnapshotChecked()) {
            try {
                CreateSnapshotBlock createSnapshot = deployBlock.getCreateSnapshotBlock();
                String snapshotName = envVars.expand(createSnapshot.getSnapshotName());
                String description = "Snapshot of successful build environment.";

                listener.getLogger().println("Creating environment snapshot '" + snapshotName
                        + "' in UrbanCode Deploy.");
                appClient.createSnapshotOfEnvironment(deployEnv, deployApp, snapshotName, description);
                listener.getLogger().println("Successfully created environment snapshot.");
            }
            catch (IOException ex) {
                throw new AbortException("Failed to create envrionment snapshot: " + ex.getMessage());
            }
            catch (JSONException ex) {
                throw new AbortException("An error occurred while processing the JSON object for the snapshot " +
                                         "request: " + ex.getMessage());
            }

        }

        long duration = (new Date().getTime() - startTime) / 1000;

        listener.getLogger().println("Finished the deployment in " + duration + " seconds");
        listener.getLogger().println("The deployment result is " + deploymentResult + ". See the UrbanCode Deploy deployment " +
                                     "logs for details.");
    }

    /**
     * Convert string of newline separated component:version to HashMap required by AppClient
     *
     * @param comopnentVersionsRaw
     * @return A HashMap containing the components with their version lists
     * @throws AbortException
     */
    private Map<String, List<String>> readComponentVersions(String componentVersionsRaw) throws AbortException {
        Map<String, List<String>> componentVersions = new HashMap<String, List<String>>();

        for (String cvLine : componentVersionsRaw.split("\n")) {
            if(!cvLine.isEmpty() && cvLine != null) {
                int delim = cvLine.indexOf(':');

                if (delim <= 0) {
                    throw new AbortException("Component/version pairs must be of the form {Component}:{Version #}");
                }

                String component = cvLine.substring(0, delim).trim();

                List<String> versionList = componentVersions.get(component);

                // create new list of versions if no versions have been added
                if (versionList == null) {
                    versionList = new ArrayList<String>();
                    componentVersions.put(component, versionList);
                }

                // update existing list of versions
                String version = cvLine.substring(delim + 1).trim();
                versionList.add(version);
            }
        }

        return componentVersions;
    }

    /**
     * Check the result of an application process
     *
     * @param appClient
     * @param procId
     * @return A boolean value stating whether the process is finished
     * @throws AbortException
     */
    private String checkDeploymentProcessResult(String procId)
    throws AbortException {
        String deploymentResult;

        try {
            deploymentResult = appClient.getApplicationProcessStatus(procId);
        }
        catch (Exception ex) {
            throw new AbortException("Failed to acquire status of application process with id '" + procId + "' : "
                                     + ex.getMessage());
        }

        return deploymentResult;
    }
}