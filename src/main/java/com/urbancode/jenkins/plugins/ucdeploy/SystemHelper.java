/*
 * Licensed Materials - Property of IBM Corp.
 * IBM UrbanCode Release
 * IBM UrbanCode Deploy
 * IBM UrbanCode Build
 * IBM AnthillPro
 * (c) Copyright IBM Corporation 2002, 2017. All Rights Reserved.
 *
 * U.S. Government Users Restricted Rights - Use, duplication or disclosure restricted by
 * GSA ADP Schedule Contract with IBM Corp.
 */
package com.urbancode.jenkins.plugins.ucdeploy;

import hudson.AbortException;
import hudson.model.TaskListener;

import java.io.IOException;
import java.net.URI;

import org.apache.http.impl.client.DefaultHttpClient;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import com.urbancode.ud.client.SystemClient;

/**
 * This class allows use of the UrbanCode Deploy REST client
 * to make system related REST calls
 */
public class SystemHelper {
    private SystemClient sysClient;

    public SystemHelper(URI ucdUrl, DefaultHttpClient httpClient, TaskListener listener) {
        sysClient = new SystemClient(ucdUrl, httpClient);
    }

    public boolean isMaintenanceEnabled() throws AbortException {
        boolean maintenanceEnabled;

        try {
            JSONObject systemConfig = sysClient.getSystemConfiguration();
            maintenanceEnabled = systemConfig.getBoolean("enableMaintenanceMode");
        }
        catch (IOException ex) {
            throw new AbortException("Invalid http response code returned when acquiring UCD system configuration:"
                    + ex.getMessage());
        }
        catch (JSONException ex) {
            throw new AbortException("Failed to acquire UCD system configuration: " + ex.getMessage());
        }

        return maintenanceEnabled;
    }
}