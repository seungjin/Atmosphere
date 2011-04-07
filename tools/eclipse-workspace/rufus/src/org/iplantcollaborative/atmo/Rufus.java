package org.iplantcollaborative.atmo;

import java.util.List;
import java.util.Map;
import com.gdevelop.gwt.syncrpc.SyncProxy;
import edu.ucsb.eucalyptus.admin.client.*;
import edu.ucsb.eucalyptus.util.UserManagement;
import org.apache.commons.codec.digest.DigestUtils;
import java.net.*;
import java.io.*;
import com.esotericsoftware.yamlbeans.*;


/*

  // call from jdbc
  euca_frontend_url <- get from the server
  euca_server's admin password
  //

  // user inputs
  new user's login name
  new user's username
  new user's email


*/



public class Rufus {

    private static String euca_frontend_admin_pass;

	//private static static EucalyptusWebBackend rpcService = (EucalyptusWebBackend) SyncProxy.newProxyInstance(EucalyptusWebBackend.class, "https://150.135.78.86:8443/", "EucalyptusWebBackend");
    private static EucalyptusWebBackend rpcService;
    	
    public static void main(String args[]) throws com.google.gwt.user.client.rpc.SerializableException {
		//Downloadin Zip files for a user
		//String zipDownloadUsername = "user100";
		//downloadzipfiles(zipDownloadUsername);
		
        String newusername = args[0];
        String name = args[1];
        String email = args[2];
        String newuserpassword = args[3];
        
        Map confMap = getConfigs();

        euca_frontend_admin_pass = confMap.get("euca_frontend_admin_pass").toString();

        rpcService = (EucalyptusWebBackend) SyncProxy.newProxyInstance(EucalyptusWebBackend.class, confMap.get("euca_frontend_url").toString(), "EucalyptusWebBackend");
        
        //Creating a new user
		createUser(newusername,name,email,newuserpassword);
		
		// Getting access key and secret key of a user
		//String user = "admin";
		UserInfoWeb userW = getUserInfo(newusername);
		System.out.println("<newuser><access_key>"+userW.getQueryId()+"</access_key><secret_key>"+userW.getSecretKey()+"</secret_key></newuser>");
	}
   
    private static Map getConfigs() {
        
        Object object = new Object();
        try {
        	
            YamlReader reader = new YamlReader(new FileReader("config.yaml"));
            object = reader.read();
            //System.out.println(object);
            Map map = (Map)object;
            //System.out.println(map.get("euca_frontend_url"));
            //System.out.println(map.get("euca_frontend_admin_pass"));      
        } catch (FileNotFoundException e) {
            System.out.println("config.yaml file not found");

        } catch (YamlException e) {
            System.out.println("Yaml Exception");
        }

        Map map = (Map)object;
        return map;
       
    }
	
	public static void createUser(String newusername, String name, String email, String newuserpassword) throws com.google.gwt.user.client.rpc.SerializableException {
		UserInfoWeb userToSave = new UserInfoWeb(newusername,name,email,DigestUtils.md5Hex(newuserpassword));
		userToSave.setIsConfirmed(true);
		
		String sessionid = getAdminSession();
        rpcService.addUserRecord(sessionid,userToSave);
        
		UserInfoWeb u = getUserInfo(newusername);
		rpcService.performAction(sessionid, "confirm", u.getConfirmationCode() );
	}
	
	private static String getAdminSession() throws com.google.gwt.user.client.rpc.SerializableException {
        String adminpassmd5 = DigestUtils.md5Hex(euca_frontend_admin_pass);
		return rpcService.getNewSessionID("admin",adminpassmd5);
	}
	
	public static void downloadzipfiles(String username) throws com.google.gwt.user.client.rpc.SerializableException {
		rpcService.getNewSessionID(username,DigestUtils.md5Hex(username));
		UserInfoWeb u = getUserInfo(username);
		String weburl = "https://150.135.78.86:8443/"+
		"getX509?user=" + username +
		"&keyValue=" + username +
		"&code=" + u.getCertificateCode();
		copyURL(weburl, username+".zip");
		System.out.println(username+".zip created successfully");
		
}

	
	
	public static UserInfoWeb getUserInfo(String username) throws com.google.gwt.user.client.rpc.SerializableException {
		String sessionid = getAdminSession();
		List<UserInfoWeb> userList = rpcService.getUserRecord(sessionid,"*");
		for ( UserInfoWeb u : userList) {
			if(u.getUserName().equals(username)) {
				return u;
			}
		}
		return null;
	}
	
	private static void copyURL(String u, String outfile) {
		try
	      {
	          java.net.URL url  = new java.net.URL(u);
	          InputStream is = url.openStream();
	          System.out.flush();
	          FileOutputStream fos=null;
	          
	          fos = new FileOutputStream(outfile);
	          int oneChar, count=0;
	          while ((oneChar=is.read()) != -1)
	          {
	             fos.write(oneChar);
	             count++;
	          }
	          is.close();
	          fos.close();
	          
	      }
	      catch (MalformedURLException e) { 
	    	  System.err.println(e.toString()); 
	      }
	      catch (IOException e) { 
	    	  System.err.println(e.toString()); 
	      }
	  
	}
	
	
}