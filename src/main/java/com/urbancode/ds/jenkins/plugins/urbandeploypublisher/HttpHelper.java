package com.urbancode.ds.jenkins.plugins.urbandeploypublisher;

import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;


public class HttpHelper {

    static {
        // register the OpenSSLProtocolSocketFactory to allow for self-signed certificates
        com.urbancode.commons.util.ssl.XTrustProvider.install();
    }
    
    final private HttpClient httpClient = new DefaultHttpClient();

    public void setUsernameAndPassword(String username, String password) {
        UsernamePasswordCredentials clientCredentials = new UsernamePasswordCredentials(username, password);
        ((AbstractHttpClient) httpClient).getCredentialsProvider().setCredentials(AuthScope.ANY, clientCredentials);
    }
    
    public String getContent(String url) 
    throws ParseException, IOException {
        return getContentWithHeaders(url, null);
    }
    
    public String getContentWithHeaders(String url, Map<String, String> headerProps) 
    throws ParseException, IOException {
        HttpGet httpGet = new HttpGet(url);
        String result = null;
        try {
            if (headerProps != null) {
                for (Entry<String, String> headerProp : headerProps.entrySet()) {
                    httpGet.setHeader(headerProp.getKey(), headerProp.getValue());
                }
            }
            HttpResponse response = httpClient.execute(httpGet);
            int responseCode = response.getStatusLine().getStatusCode();
            String responsed = response.toString();
            if (!isGoodResponseCode(responseCode)) {
                throw new RuntimeException("Getting " + url + " returned error code " +responsed + " \n " + responseCode);
            }
            result = EntityUtils.toString(response.getEntity());
        }

        finally {
            httpGet.releaseConnection();
        }
        return result;
    }

    public void postContent(String url, String content) 
    throws ParseException, IOException {
        HttpPost httpPost = new HttpPost(url);
        try {
            StringEntity entity = new StringEntity(content);
            httpPost.setEntity(entity);
            HttpResponse response = httpClient.execute(httpPost);
            int responseCode = response.getStatusLine().getStatusCode();
            if (!isGoodResponseCode(responseCode)) {
                String responseContent = EntityUtils.toString(response.getEntity());
                throw new RuntimeException("Posting to " + url + " returned error code " +
                		responseCode + ": " +  responseContent);
            }
        }
        finally {
            httpPost.releaseConnection();
        }
    }
    
    public void putContent(String url, String content)
    throws ClientProtocolException, IOException {
        putContentWithHeaders(url, content, null);
    }

    public void putContentWithHeaders(String url, String content, Map<String, String> headerProps)
    throws ClientProtocolException, IOException {
        HttpPut httpPut = new HttpPut(url);
        try {
            StringEntity entity = new StringEntity(content);
            httpPut.setEntity(entity);
            if (headerProps != null) {
                for (Entry<String, String> headerProp : headerProps.entrySet()) {
                    httpPut.setHeader(headerProp.getKey(), headerProp.getValue());
                }
            }
            HttpResponse response = httpClient.execute(httpPut);
            int responseCode = response.getStatusLine().getStatusCode();
            if (!isGoodResponseCode(responseCode)) {
                throw new RuntimeException("Putting to " + url + " returned error code " + responseCode);
            }
        }
        finally {
            httpPut.releaseConnection();
        }
    }

    private boolean isGoodResponseCode(int responseCode) {
        return responseCode >= 200 && responseCode < 300;
    }

}