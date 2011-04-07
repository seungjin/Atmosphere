/*
 * Copyright www.gdevelop.com.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.gdevelop.gwt.syncrpc;


//import com.google.gdata.client.GoogleAuthTokenFactory;
//import com.google.gdata.util.AuthenticationException;
import com.google.gwt.user.client.rpc.StatusCodeException;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;

import java.lang.reflect.Proxy;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;


/**
 * Sync Proxy for GWT RemoteService
 * Usage:
 * MyServiceInterface myService = newProxyInstance(MyServiceInterface.class,
 *    "http://localhost:8888/myapp/", "myServiceServlet", policyName);
 *  where policyName is the file name (with gwt.rpc extenstion) generated
 *  by GWT RPC backend
 *
 * Or
 * MyServiceInterface myService = newProxyInstance(MyServiceInterface.class,
 *    "http://localhost:8888/myapp/", "myServiceServlet");
 * In this case, the SyncProxy search for the appropriate policyName file in
 * the system classpath
 */
public class SyncProxy {
  private static final String GWT_PRC_POLICY_FILE_EXT = ".gwt.rpc";
  private static final Map<String, String> POLICY_MAP = new HashMap<String, String>();
  static{
    String classPath = System.getProperty("java.class.path");
    StringTokenizer st = new StringTokenizer(classPath, File.pathSeparator);
    while (st.hasMoreTokens()){
      String path = st.nextToken();
      File f = new File(path);
      if (f.isDirectory()){
        searchPolicyFile(path);
      }
      // TODO: Search in jar, zip files
    }
  }
  
  private static void searchPolicyFile(String path){
    String policyName = null;
    
    File f = new File(path);
    String[] children = f.list(new FilenameFilter(){
      public boolean accept(File dir, String name) {
        if (name.endsWith(GWT_PRC_POLICY_FILE_EXT)){
          return true;
        }
        return false;
      }
    });
    for (String child : children){
      BufferedReader reader;
      try {
        reader = new BufferedReader(new FileReader(new File(path ,child)));
        String line = reader.readLine();
        while (line != null){
          int pos = line.indexOf(", false, false, false, false, _, ");
          if (pos > 0){
            policyName = child.substring(0, child.length() - GWT_PRC_POLICY_FILE_EXT.length());
            POLICY_MAP.put(line.substring(0, pos), policyName);
          }
          line = reader.readLine();
        }
      } catch (IOException e) {
        e.printStackTrace();
        // ignore
      }
    }
  }
  
  private static final String GAE_SERVICE_NAME = "ah";
  private static final CookieManager cookieManager = RemoteServiceSyncProxy.cookieManager;

  /**
   *
   * @param serviceIntf The remote service interface
   * @param moduleBaseURL Base URL
   * @param remoteServiceRelativePath The remote service servlet relative path
   * @param policyName Policy name (*.gwt.rpc) generated by GWT RPC backend
   * @return A new proxy object which implements the service interface serviceIntf
   */
  @SuppressWarnings("unchecked")
  public static Object newProxyInstance(Class serviceIntf, String moduleBaseURL, 
                                       String remoteServiceRelativePath, 
                                       String policyName){
    return Proxy.newProxyInstance(SyncProxy.class.getClassLoader(), 
                new Class[]{serviceIntf}, 
                new RemoteServiceInvocationHandler(moduleBaseURL, 
                                                   remoteServiceRelativePath, 
                                                   policyName));
  }

  /**
   * Same as above with the policyName is auto-determined base on policy files
   * in the system classpath
   */
  @SuppressWarnings("unchecked")
  public static Object newProxyInstance(Class serviceIntf, String moduleBaseURL, 
                                       String remoteServiceRelativePath){
    return newProxyInstance(serviceIntf, moduleBaseURL, remoteServiceRelativePath, 
                            POLICY_MAP.get(serviceIntf.getName()));
  }
  
  
  
  /**
   * 
   * @param loginUrl Should be http://localhost:8888 for local development mode 
   * or https://example.appspot.com for deployed app
   * @param serviceUrl Should be http://localhost:8888/yourApp.html 
   * or http[s]://example.appspot.com/yourApp.html
   * @param email
   * @param password
   * @throws IOException
   * @throws AuthenticationException
   */
  /*
  public static void loginGAE(String loginUrl, String serviceUrl, 
                              String email, String password) throws IOException,
                                                      AuthenticationException {
    boolean localDevMode = false;
    if (loginUrl.startsWith("http://localhost")){
      localDevMode = true;
    }
    
    if (localDevMode){
      loginUrl += "/_ah/login";
      URL url = new URL(loginUrl);
      email = URLEncoder.encode(email, "UTF-8");
      serviceUrl = URLEncoder.encode(serviceUrl, "UTF-8");
      String requestData = "email=" + email + "&continue=" + serviceUrl;

      HttpURLConnection connection = (HttpURLConnection)url.openConnection();
      connection.setDoInput(true);
      connection.setDoOutput(true);
      connection.setInstanceFollowRedirects(false);
      connection.setRequestMethod("POST");
      connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
      connection.setRequestProperty("Content-Length", "" + requestData.length());
      cookieManager.setCookies(connection);
      
      OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
      writer.write(requestData);
      writer.flush();
      writer.close();
      cookieManager.storeCookies(connection);
      int statusCode = connection.getResponseCode();

      if ((statusCode != HttpURLConnection.HTTP_OK) 
          && (statusCode != HttpURLConnection.HTTP_MOVED_TEMP)){
        String responseText = getResposeText(connection);
        throw new StatusCodeException(statusCode, responseText);
      }
    }else{
      GoogleAuthTokenFactory factory = new GoogleAuthTokenFactory(GAE_SERVICE_NAME, "", null);
      // Obtain authentication token from Google Accounts
      String token = factory.getAuthToken(email, password, null, null, GAE_SERVICE_NAME, "");
      loginUrl = loginUrl + "/_ah/login?continue=" + 
                 URLEncoder.encode(serviceUrl, "UTF-8") + "&auth=" + token;
      URL url = new URL(loginUrl);

      HttpURLConnection connection = (HttpURLConnection)url.openConnection();
      connection.setDoInput(true);
      connection.setDoOutput(true);
      connection.setInstanceFollowRedirects(false);
      connection.setRequestMethod("GET");
      connection.connect();
      // Get cookie returned from login service
      cookieManager.storeCookies(connection);
      int statusCode = connection.getResponseCode();
      if ((statusCode != HttpURLConnection.HTTP_OK) 
          && (statusCode != HttpURLConnection.HTTP_MOVED_TEMP)){
        String responseText = getResposeText(connection);
        throw new StatusCodeException(statusCode, responseText);
      }
    }
  }
  */
  
  private static String getResposeText(HttpURLConnection connection) throws IOException {
    InputStream is = connection.getInputStream();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    byte[] buffer = new byte[1024];
    int len;
    while ((len = is.read(buffer)) > 0){
      baos.write(buffer, 0, len);
    }
    String responseText = baos.toString("UTF8");
    return responseText;
  }
}