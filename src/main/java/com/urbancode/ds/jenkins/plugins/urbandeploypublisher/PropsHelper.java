package com.urbancode.ds.jenkins.plugins.urbandeploypublisher;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.ws.rs.core.UriBuilder;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;

import hudson.model.BuildListener;

public class PropsHelper {

    private HttpHelper httpHelper = new HttpHelper();

    public void setComponentVersionProperties(String url, List<String> componentNames, String versionName, String properties, String user, String pass, BuildListener listener) {
        if (properties.length() > 0) {
            try {
                Properties propertiesToSet = new Properties();
                propertiesToSet.load(new StringReader(properties));
                httpHelper.setUsernameAndPassword(user, pass);

                for (String componentName : componentNames) {
                    setComponentVersionProperties(url, componentName, versionName, propertiesToSet, listener);
                }
            }
            catch (Exception e) {
                listener.getLogger().println("An error occured while parsing the properties: " + e.getMessage());
            }
        }
    }

    public void setComponentVersionProperties(String url, String componentName, String versionName, Properties propertiesToSet, BuildListener listener) {
        if (!propertiesToSet.isEmpty()) {
            // get the component version
            try {
                String versionsUrl = UriBuilder.fromUri(url).segment("rest", "deploy", "component", componentName, "versions", "false").build().toString();
                JSONArray versionsJson = new JSONArray(httpHelper.getContent(versionsUrl));
                String versionId = null;
                for (int versionIndex = 0; versionIndex < versionsJson.length(); versionIndex++) {
                    JSONObject versionJson = versionsJson.getJSONObject(versionIndex);
                    if (versionName.equals(versionJson.getString("name"))) {
                        versionId = versionJson.getString("id");
                        break;
                    }
                }

                if (versionId == null) {
                    throw new Exception("Version " + versionName + " not found on component " + componentName + ".");
                }

                //you can only create properties on the version that are defined on the component
                String componentUrl = UriBuilder.fromUri(url).segment("rest", "deploy", "component", componentName).build().toString();
                listener.getLogger().println("Retrieving component");
                String componentContent = httpHelper.getContent(componentUrl);

                JSONObject componentJson= new JSONObject(componentContent);
                String componentId = componentJson.getString("id");

                String propSheetDefPath = componentJson.getJSONObject("versionPropSheetDef").getString("path");
                propSheetDefPath += ".-1";
                String propDefsUrl = UriBuilder.fromUri(url).segment("property", "propSheetDef", propSheetDefPath, "propDefs").build().toString();
                listener.getLogger().println("Retrieving property definitions");
                String propSheetDefContent = httpHelper.getContent(propDefsUrl);
                JSONArray propSheetDefJson = new JSONArray(propSheetDefContent);

                List<String> propertiesToCreate = new ArrayList<String>();
                for (Object propertyName : propertiesToSet.keySet()) {
                    boolean foundProperty = false;
                    for (int psIndex = 0; psIndex < propSheetDefJson.length(); psIndex++) {
                        JSONObject propSheetDef = propSheetDefJson.getJSONObject(psIndex);
                        if (propertyName.equals(propSheetDef.getString("name"))) {
                            foundProperty = true;
                            break;
                        }
                    }
                    if (!foundProperty) {
                        propertiesToCreate.add((String) propertyName);
                    }
                }

                if (!propertiesToCreate.isEmpty()) {
                    listener.getLogger().println("Creating non-existent property definitions");
                    for (String propertyToCreate : propertiesToCreate) {
                        // {"id":"caa95ea5-cf17-4369-80a4-9abcbffe0da0","name":"my_prop_def","label":"my_prop_def","type":"TEXT",
                        // "value":"","required":false,"description":"","inherited":false}
                        JSONObject propertyCreateJson = new JSONObject();
                        propertyCreateJson.put("name", propertyToCreate);
                        propertyCreateJson.put("label", "");
                        propertyCreateJson.put("type", "TEXT");
                        propertyCreateJson.put("value", "");
                        propertyCreateJson.put("required", false);
                        propertyCreateJson.put("description", "");
                        propertyCreateJson.put("inherited", false);
                        httpHelper.putContent(propDefsUrl, propertyCreateJson.toString());
                        listener.getLogger().println("Created property definition for: " + propertyToCreate);
                    }
                }

                // now set the properties
                String versionUrl = UriBuilder.fromUri(url).segment("rest", "deploy", "version", versionId).build().toString();
                listener.getLogger().println("Retrieving version");
                JSONObject versionJson = new JSONObject(httpHelper.getContent(versionUrl));

                String propSheetPath = null;
                int propSheetVersion = 1;
                JSONArray versionPropSheetsJson = versionJson.getJSONArray("propSheets");
                for (int vpsIndex = 0; vpsIndex < versionPropSheetsJson.length(); vpsIndex++) {
                    JSONObject versionPropSheetJson = versionPropSheetsJson.getJSONObject(vpsIndex);
                    if (!versionPropSheetJson.has("name")) {
                        propSheetPath = versionPropSheetJson.getString("path");
                        propSheetVersion = Integer.valueOf(versionPropSheetJson.getString("version"));
                        break;
                    }
                }

                if (propSheetPath == null) {
                    throw new Exception("Did not find the location to update properties for the component version.");
                }

                String propSheetPathToUpdate = propSheetPath + "." + propSheetVersion;
                String versionPropSheetUrl = UriBuilder.fromUri(url).segment("property", "propSheet", propSheetPathToUpdate, "allPropValues").build().toString();
                JSONObject propertiesJson = new JSONObject();
                listener.getLogger().println("Updating properties");
                for (Object propertyName : propertiesToSet.keySet()) {
                    String propertyValue = propertiesToSet.containsKey((String) propertyName) ? propertiesToSet.getProperty((String) propertyName) : "";

                    propertiesJson.put((String) propertyName, propertyValue);
                }
                Map<String, String> headerProps = new HashMap<String, String>();
                headerProps.put("version", String.valueOf(propSheetVersion));
                httpHelper.putContentWithHeaders(versionPropSheetUrl, propertiesJson.toString(), headerProps);

                listener.getLogger().println("component: " + componentName + " version:" + versionName + " properties updated successfully");
            }
            catch (Exception e) {
                listener.getLogger().println("An error occured while adding properties to the version: " + e.getMessage());
            }
        }
    }
}
