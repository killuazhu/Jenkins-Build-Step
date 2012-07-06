package com.urbancode.ds.jenkins.plugins.urbandeploypublisher;

import com.urbancode.commons.util.https.OpenSSLProtocolSocketFactory;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.commons.lang.StringUtils;

import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UrbanDeploySite {

	/** The profile name. */
	private String profileName;

	/** The url. */
	private String url;

	/** The username. */
	private String user;

	/** The password. */
	private String password;

	/**
	 * Instantiates a new UrbanDeploy site.
	 *
	 * @param profileName
	 *          the profile name
	 * @param url
	 *          the url of the UrbanDeploy instance
	 * @param username
	 *          the username
	 * @param password
	 *          the password
	 */
	 public UrbanDeploySite() {

	}
	 
	public UrbanDeploySite(String profileName, String url, String user, String password) {
		this.profileName = profileName;
		this.url = url;
		this.user = user;
		this.password = password;
	}


	/**
	 * Gets the display name.
	 *
	 * @return the display name
	 */
	public String getDisplayName() {
		if (StringUtils.isEmpty(profileName)) {
			return url;
		} else {
			return profileName;
		}
	}

	/**
	 * Gets the profile name.
	 *
	 * @return the profile name
	 */
	public String getProfileName() {
		return profileName;
	}

	/**
	 * Sets the profile name.
	 *
	 * @param profileName
	 *          the new profile name
	 */
	public void setProfileName(String profileName) {
		this.profileName = profileName;
	}

	/**
	 * Gets the url.
	 *
	 * @return the url
	 */
	public String getUrl() {
		return url;
	}

	/**
	 * Sets the url.
	 *
	 * @param url
	 *          the new url
	 */
	public void setUrl(String url) {
		this.url = url;
        if (this.url != null) {
            this.url = this.url.replaceAll("\\\\", "/");
        }
        while (this.url != null && this.url.endsWith("/")) {
            this.url = this.url.substring(0, this.url.length() - 2);
        }
	}

	/**
	 * Gets the username.
	 *
	 * @return the username
	 */
	public String getUser() {
		return user;
	}

	/**
	 * Sets the username.
	 *
	 * @param username
	 *          the new username
	 */
	public void setUser(String user) {
		this.user = user;
	}

	/**
	 * Gets the password.
	 *
	 * @return the password
	 */
	public String getPassword() {
		return password;
	}

	/**
	 * Sets the password.
	 *
	 * @param password
	 *          the new password
	 */
	public void setPassword(String password) {
		this.password = password;
	}

    public void verifyConnection() throws Exception {
        URI uri = UriBuilder.fromPath(url).path("rest").path("state").build();
        executeJSONGet(uri);
    }

    public String executeJSONGet(URI uri) throws Exception {
        String result = null;
        HttpClient httpClient = new HttpClient();

        if ("https".equalsIgnoreCase(uri.getScheme())) {
            ProtocolSocketFactory socketFactory = new OpenSSLProtocolSocketFactory();
            Protocol https = new Protocol("https", socketFactory, 443);
            Protocol.registerProtocol("https", https);
        }

        GetMethod method = new GetMethod(uri.toString());
        try {
            HttpClientParams params = httpClient.getParams();
            params.setAuthenticationPreemptive(true);

            UsernamePasswordCredentials clientCredentials = new UsernamePasswordCredentials(user, password);
            httpClient.getState().setCredentials(AuthScope.ANY, clientCredentials);

            int responseCode = httpClient.executeMethod(method);
            //if (responseCode < 200 || responseCode < 300) {
            if (responseCode == 401) {
                throw new Exception("Error connecting to UrbanDeploy: Invalid user and/or password");
            }
            else if (responseCode != 200) {
                throw new Exception("Error connecting to UrbanDeploy: " + responseCode);
            }
            else {
                result = method.getResponseBodyAsString();
            }
        }
        finally {
            method.releaseConnection();
        }

        return result;
    }

    public String executeJSONPut(URI uri, String putContents) throws Exception {
        String result = null;
        HttpClient httpClient = new HttpClient();

        if ("https".equalsIgnoreCase(uri.getScheme())) {
            ProtocolSocketFactory socketFactory = new OpenSSLProtocolSocketFactory();
            Protocol https = new Protocol("https", socketFactory, 443);
            Protocol.registerProtocol("https", https);
        }

        PutMethod method = new PutMethod(uri.toString());
        method.setRequestBody(putContents);
        method.setRequestHeader("Content-Type", "application/json");
        method.setRequestHeader("charset", "utf-8");
        try {
            HttpClientParams params = httpClient.getParams();
            params.setAuthenticationPreemptive(true);

            UsernamePasswordCredentials clientCredentials = new UsernamePasswordCredentials(user, password);
            httpClient.getState().setCredentials(AuthScope.ANY, clientCredentials);

            int responseCode = httpClient.executeMethod(method);

            //if (responseCode < 200 || responseCode < 300) {
            if (responseCode != 200 ) {
                throw new Exception("UrbanDeploy returned error code: " + responseCode);
            }
            else {
                result = method.getResponseBodyAsString();
            }
        }
        catch (Exception e) {
            throw new Exception("Error connecting to UrbanDeploy: " + e.getMessage());
        }
        finally {
            method.releaseConnection();
        }

        return result;
    }

    public String executeJSONPost(URI uri) throws Exception {
        String result = null;
        HttpClient httpClient = new HttpClient();

        if ("https".equalsIgnoreCase(uri.getScheme())) {
            ProtocolSocketFactory socketFactory = new OpenSSLProtocolSocketFactory();
            Protocol https = new Protocol("https", socketFactory, 443);
            Protocol.registerProtocol("https", https);
        }

        PostMethod method = new PostMethod(uri.toString());
        
        method.setRequestHeader("charset", "utf-8");
        try {
            HttpClientParams params = httpClient.getParams();
            params.setAuthenticationPreemptive(true);

            UsernamePasswordCredentials clientCredentials = new UsernamePasswordCredentials(user, password);
            httpClient.getState().setCredentials(AuthScope.ANY, clientCredentials);

            int responseCode = httpClient.executeMethod(method);

            if (responseCode != 200 ) {
                throw new Exception("UrbanDeploy returned error code: " + responseCode);
            }
            else {
                result = method.getResponseBodyAsString();
            }
        }
        catch (Exception e) {
            throw new Exception("Error connecting to UrbanDeploy: " + e.getMessage());
        }
        finally {
            method.releaseConnection();
        }

        return result;
    }
}
