package com.urbancode.ds.jenkins.plugins.urbandeploypublisher;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Serializable;
import java.net.URI;

import javax.ws.rs.core.UriBuilder;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;

import com.urbancode.commons.httpcomponentsutil.HttpClientBuilder;
import com.urbancode.commons.util.IO;

public class UrbanDeploySite implements Serializable {

    private static final long serialVersionUID = -8723534991244260459L;

    /** The profile name. */
	private String profileName;

	/** The url. */
	private String url;

	/** The username. */
	private String user;

	/** The password. */
	private String password;

	transient private HttpClient client;

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

	public HttpClient getClient() {
	    if (client == null) {
	        HttpClientBuilder builder = new HttpClientBuilder();
	        builder.setPreemptiveAuthentication(true);
	        builder.setUsername(user);
	        builder.setPassword(password);

	        builder.setTrustAllCerts(true);

	        client = builder.buildClient();
	    }
	    return client;
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
        HttpClient client = getClient();
        HttpGet method = new HttpGet(uri.toString());
        try {
            HttpResponse response = client.execute(method);
            int responseCode = response.getStatusLine().getStatusCode();
            //if (responseCode < 200 || responseCode < 300) {
            if (responseCode == 401) {
                throw new Exception("Error connecting to IBM UrbanCode Deploy: Invalid user and/or password");
            }
            else if (responseCode != 200) {
                throw new Exception("Error connecting to IBM UrbanCode Deploy: " + responseCode + "using URI: " + uri.toString());
            }
            else {
                result = getBody(response);
            }
        }
        finally {
            method.releaseConnection();
        }

        return result;
    }

    public String executeJSONDelete(URI uri) throws Exception {
        String result = null;
        HttpClient client = getClient();
        HttpDelete method = new HttpDelete(uri.toString());
        try {
            HttpResponse response = client.execute(method);
            int responseCode = response.getStatusLine().getStatusCode();
            if (responseCode == 401) {
                throw new Exception("Error connecting to IBM UrbanCode Deploy: Invalid user and/or password");
            }
            else if (responseCode != 200) {
                throw new Exception("Error connecting to IBM UrbanCode Deploy: " + responseCode);
            }
            else {
                result = getBody(response);
            }
        }
        finally {
            method.releaseConnection();
        }

        return result;
    }

    public String executeJSONPut(URI uri, String putContents) throws Exception {
        String result = null;

        HttpPut method = new HttpPut(uri.toString());
        HttpClient client = getClient();
        StringEntity requestEntity = new StringEntity(putContents);
        method.setEntity(requestEntity);
        try {
            HttpResponse response = client.execute(method);
            int responseCode = response.getStatusLine().getStatusCode();
            if (responseCode == 401) {
                throw new Exception("Error connecting to IBM UrbanCode Deploy: Invalid user and/or password");
            }
            else if (responseCode != 200 && responseCode != 204) {
                throw new Exception("Error connecting to IBM UrbanCode Deploy: " + responseCode);
            }
            else {
                result = getBody(response);
            }
        }
        finally {
            method.releaseConnection();
        }

        return result;
    }

    public String executeJSONPost(URI uri) throws Exception {
        String result = null;


        HttpPost method = new HttpPost(uri.toString());
        HttpClient client = getClient();

        method.setHeader("charset", "utf-8");
        try {

            HttpResponse response = client.execute(method);
            int responseCode = response.getStatusLine().getStatusCode();
            //if (responseCode < 200 || responseCode < 300) {
            if (responseCode == 401) {
                throw new Exception("Error connecting to IBM UrbanCode Deploy: Invalid user and/or password");
            }
            else if (responseCode != 200) {
                throw new Exception("Error connecting to IBM UrbanCode Deploy: " + responseCode);
            }
            else {
                result = getBody(response);
            }
        }
        finally {
            method.releaseConnection();
        }

        return result;
    }

    protected String getBody(HttpResponse response)
    throws IOException {
        StringBuilder builder = new StringBuilder();
        if(response != null && response.getEntity() != null){
        	InputStream body = response.getEntity().getContent();
            if (body != null) {
                Reader reader = IO.reader(body, IO.utf8());
                try {
                    IO.copy(reader, builder);
                }
                finally {
                    reader.close();
                }
            }
            return builder.toString();
        }
        return "";
    }
}
