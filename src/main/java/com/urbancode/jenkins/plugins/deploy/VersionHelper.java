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

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.net.URI;
import java.util.UUID;

import org.apache.http.impl.client.DefaultHttpClient;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.codehaus.jettison.json.JSONException;

import com.urbancode.ud.client.ComponentClient;
import com.urbancode.ud.client.PropertyClient;
import com.urbancode.ud.client.VersionClient;

/**
 * This class is used to provide access to the UrbanCode Deploy rest client
 * and run component version related rest calls
 *
 */
@SuppressWarnings("deprecation") // Triggered by DefaultHttpClient
public class VersionHelper {
    VersionClient versionClient;
    PropertyClient propClient;
    ComponentClient compClient;

    public VersionHelper(URI ucdUrl, DefaultHttpClient httpClient) {
        versionClient = new VersionClient(ucdUrl, httpClient);
        propClient = new PropertyClient(ucdUrl, httpClient);
        compClient = new ComponentClient(ucdUrl, httpClient);
    }

    /**
     * Creates the component version
     *
     * @return UUID of the new version
     * @throws AbortException
     */
    public UUID createComponentVersion(String version, String component, String description)
    throws AbortException {
        if (version == null || version.isEmpty() || version.length() > 255) {
            throw new AbortException("Failed to create version '" + version + "' in UrbanCode Deploy. "
                    + "UrbanCode Deploy version name length must be between 1 and  255 characters "
                    + "long. (Current length: " + version.length() + ")");
        }

        UUID versionId;

        try {
            versionId = versionClient.createVersion(component, version, description);
        }
        catch (Exception ex) {
            throw new AbortException("Failed to create component version '"
                    + version + "' on component '" + component + "' : " + ex.getMessage());
        }

        return versionId;
    }

    /**
     * Upload files to component version
     *
     * @throws AbortException
     */
    public void uploadVersionFiles(
            File workDir,
            String component,
            String version,
            String includePatterns,
            String excludePatterns)
    throws AbortException {
        String[] includes = splitFiles(includePatterns);
        String[] excludes = splitFiles(excludePatterns);

        try {
            versionClient.addVersionFiles(
                    component,
                    version,
                    workDir,
                    "",
                    includes,
                    excludes,
                    true,
                    true);
        }
        catch (Exception ex) {
            throw new AbortException("Failed to upload files to version '" + version + "' : " + ex.getMessage());
        }
    }

    /**
     * Remove the component version completely
     *
     * @param id The unique identifier of the component version
     * @throws AbortException
     */
    public void deleteComponentVersion(UUID id)
    throws AbortException {
        try {
            versionClient.deleteVersion(id);
        }
        catch (Exception ex) {
            throw new AbortException("Failed to delete component version: " + id + " : " + ex.getMessage());
        }
    }

    /**
     * Add the link on the component to the component version
     *
     * @param component
     * @param version
     * @param linkName
     * @param linkUrl
     * @throws AbortException
     */
    public void addLinkToComp(
            String component,
            String version,
            String linkName,
            String linkUrl)
    throws AbortException
    {
        try {
            compClient.addComponentVersionLink(component, version, linkName, linkUrl);
        }
        catch (Exception ex) {
            throw new AbortException("Failed to add a version link to the component '"
                    + component + "' : " + ex.getMessage());
        }
    }

    /**
     * Create and set properties on the component version
     *
     * @param component
     * @param version
     * @param properties
     * @param listener
     * @throws AbortException
     */
    public void setComponentVersionProperties(
            String component,
            String version,
            String properties,
            BuildListener listener)
    throws AbortException {
        Map<String, String> propertiesToSet = readProperties(properties);

        if (!propertiesToSet.isEmpty()) {
            JSONObject propSheetDef;
            String propSheetDefId;
            String propSheetDefPath;
            JSONArray existingPropDefJsonArray;

            // acquire prop sheet definition and it's existing propDefs
            try {
                propSheetDef = compClient.getComponentVersionPropSheetDef(component);
                propSheetDefId = (String) propSheetDef.get("id");
                propSheetDefPath = (String) propSheetDef.get("path");
                existingPropDefJsonArray = propClient.getPropSheetDefPropDefs(propSheetDefPath);
            }
            catch (IOException ex) {
                throw new AbortException("An error occurred acquiring component object for component '"
                        + component + "' : " + ex.getMessage());
            }
            catch (JSONException e) {
                throw new AbortException("An error occurred acquiring property definitions of the "
                        + "version property sheet for component '" + component + "' : " + e.getMessage());
            }

            // update existing properties
            for (int i = 0; i < existingPropDefJsonArray.length(); i++) {
                JSONObject propDef;
                String propName;

                try {
                    propDef = existingPropDefJsonArray.getJSONObject(i);
                    propName = propDef.getString("name");
                }
                catch (JSONException ex) {
                    throw new AbortException("An error occurred acquiring an existing property definition "
                            + "for component '" + component + "' : " + ex.getMessage());
                }

                String propValue = propertiesToSet.get(propName);

                if (propValue != null) {
                    try {
                        listener.getLogger().println("Setting version property " + propName);
                        versionClient.setVersionProperty(version, component, propName, propValue, false);
                        listener.getLogger().println("Successfully updated version property " + propName);
                    }
                    catch (IOException ex) {
                        throw new AbortException("An error occurred while setting the value of an existing property '"
                                + propName + "' : " + ex.getMessage());
                    }
                }

                propertiesToSet.remove(propName);
            }

            // create new properties
            if (!propertiesToSet.isEmpty()) {
                listener.getLogger().println("Creating new property definitions.");
                UUID propSheetDefUUID = UUID.fromString(propSheetDefId);

                for (Map.Entry<String, String> property : propertiesToSet.entrySet()) {
                    String propName = property.getKey();
                    String propDescription = "";
                    String propLabel = "";
                    Boolean required = false;
                    String propType = "TEXT";
                    String propValue = property.getValue();

                    try {
                        listener.getLogger().println("Creating property definition for: " + propName);
                        propClient.createPropDef(
                                propSheetDefUUID,
                                propSheetDefPath,
                                propName,
                                propDescription,
                                propLabel,
                                required,
                                propType,
                                "");
                        versionClient.setVersionProperty(version, component, propName, propValue, false);
                        listener.getLogger().println("Successfully created version property " + propName);
                    }
                    catch (IOException ex) {
                        throw new AbortException("An error occurred while creating a new version property '" + propName
                                + "' for version '" + version + "' : " + ex.getMessage());
                    }
                    catch (JSONException ex) {
                        throw new AbortException("An error occurred creating the property definition '" + propName
                                + "' on property sheet with UUID '" + propSheetDefUUID + "' : " + ex.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Run import versions on a component
     *
     * @param component The component to import versions on
     * @param properties Runtime properties to specify for version import
     * @throws AbortException
     */
    public void importVersions(String component, String properties) throws AbortException {
        Map<String, String> propertiesToSet = readProperties(properties);

        try {
            compClient.importComponentVersions(component, propertiesToSet);
        }
        catch (IOException ex) {
            throw new AbortException("An error occurred while importing component versions on component '"
                    + component + "' : " + ex.getMessage());
        }
        catch(JSONException ex) {
            throw new AbortException("An error occurred while creating JSON version import object : "
                    + ex.getMessage());
        }
    }

    /**
     * Load properties into a properties map
     *
     * @param properties The unparsed properties to load
     * @return The loaded properties map
     * @throws AbortException
     */
    private Map<String, String> readProperties(String properties) throws AbortException {
        Map<String, String> propertiesToSet = new HashMap<String, String>();

        for (String line : properties.split("\n")) {
            String[] propDef = line.split("=");

            if (propDef.length >= 2) {
                String propName = propDef[0].trim();
                String propVal = propDef[1].trim();
                propertiesToSet.put(propName, propVal);
            }
            else {
                throw new AbortException("Missing property delimiter '=' in property definition '" + line + "'");
            }
        }

        return propertiesToSet;
    }

    /**
     * Split a string of filenames by newline and remove empty/null entries
     *
     * @param patterns Newline separated list of file patterns
     * @return Array of file patterns
     */
    private String[] splitFiles(String patterns) {
        List<String> newList = new ArrayList<String>();

        String[] patternList = patterns.split("\n");

        for (String pattern : patternList) {
            if (pattern != null && pattern.trim().length() > 0) {
                newList.add(pattern.trim());
            }
        }

        return newList.toArray(new String[newList.size()]);
    }

}