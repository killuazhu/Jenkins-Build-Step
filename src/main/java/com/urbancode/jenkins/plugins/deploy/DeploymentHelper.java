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

import com.urbancode.ud.client.ApplicationClient;

import hudson.AbortException;
import hudson.model.BuildListener;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.http.impl.client.DefaultHttpClient;

/**
 * This class executes rest calls for deploying versions to UCD using
 * uDeployRestClient and run rest calls related to running a deployment
 * application process
 *
 */
@SuppressWarnings("deprecation") // Triggered by DefaultHttpClient
public class DeploymentHelper {
    ApplicationClient appClient;

    public DeploymentHelper(URI ucdUrl, DefaultHttpClient httpClient) {
        appClient = new ApplicationClient(ucdUrl, httpClient);
    }

    /**
     * Trigger application deployment process with latest versions of each
     * component.
     *
     * @param appClient
     * @param application
     * @param environment
     * @param process
     * @param componentVersions
     * @param version
     * @param listener
     * @return The id of the application process request
     * @throws AbortException
     */
    public String createDefaultProcessRequest(
            String application,
            String process,
            Map<String, List<String>> componentVersions,
            String environment,
            Boolean onlyChanged,
            String snapshot,
            BuildListener listener)
    throws AbortException {
        listener.getLogger().println("Creating application process deployment request.");

        UUID appProc;

        try {
            appProc = appClient.requestApplicationProcess(
                    application,
                    process,
                    "",
                    environment,
                    snapshot,
                    onlyChanged,
                    componentVersions);
        }
        catch (Exception ex) {
            throw new AbortException("Failed to run application process '" + process
                    + "' on application '" + application + "' : " + ex.getMessage());
        }

        listener.getLogger().println("Successfully created application process deployment request.");

        return appProc.toString();
    }

    /**
     * Check the result of an application process
     *
     * @param appClient
     * @param procId
     * @return A boolean value stating whether the process is finished
     * @throws AbortException
     */
    public String checkDeploymentProcessResult(String procId)
    throws AbortException {
        String deploymentResult;

        try {
            deploymentResult = appClient.getApplicationProcessStatus(procId);
        }
        catch (Exception ex) {
            throw new AbortException("Failed to acquire status of application process with id '"
                    + procId + "' : " + ex.getMessage());
        }

        return deploymentResult;
    }
}