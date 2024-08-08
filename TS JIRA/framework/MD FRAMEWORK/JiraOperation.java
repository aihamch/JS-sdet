package com.optum.coe.automation.rally;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.apache.http.HttpEntity;
import org.apache.http.ParseException;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class JiraOperation {

	// Initialization of the class member variables. This section can be updated in future if more member variables are added during integration if needed. 
	
	private String jiraBaseURL; 
	private String jiraApiKey;
	private String jiraProjectKey;
	private String max_chunk; 
	private String tcAttachmentDownloadLocation;
	
	// Logger Initialization for JiraOperation Class
	
	private static final Logger logger = LogManager.getLogger();
	
	/* A Constructor loads the value from .properties file. These value will be loaded as soon as a object is created for this class.
	 * Jira Base URL, Jira API Key, Jira Project Key, Max Chunk values are loaded from .properties file
	 * max_chunk value refers that how many test cases should be migrated for a run. */
	
	public JiraOperation() {
		
		jiraBaseURL=ConfigLoader.getConfigValue("JIRA_BASE_URL");
		jiraApiKey=ConfigLoader.getConfigValue("JIRA_API_TOKEN");
		jiraProjectKey=ConfigLoader.getConfigValue("JIRA_PROJECT_KEY");
		max_chunk=ConfigLoader.getConfigValue("MAX_VALUE_CHUNK");
		tcAttachmentDownloadLocation=ConfigLoader.getConfigValue("TEST_CASE_ATTACHMENT_LOCATION");
		logger.info("Jira values for the project key " + jiraProjectKey +" are assiged from rally_migration_config.properties file");
		logger.log(Level.getLevel("VERBOSE"), "Below the values assigned from rally_migration_config.properties file. \nJira Base URL - " + jiraBaseURL + "\nJira Project Key " + jiraProjectKey + "\nMax Chunk value - " + max_chunk + "\nTest Attachment Download location - " + tcAttachmentDownloadLocation);

	}
	
	// Create a method to get non migrated testcase keys using JIRA REST Api and save them to Testcase array list
	
	public ArrayList<String> getJiraNonMigratedTestcaseKeys() {
		
		// An ArrayList is used to store the testcases keys from Jira response
		
		ArrayList<String> testCaseKeys = new ArrayList<String>();
		
		// Preparation of URL string building.
		
		String encodededKey = null;
		try {
			encodededKey = URLEncoder.encode("projectKey = " + jiraProjectKey + " AND \"Migrate Test to Rally\" = true AND \"TestCase Migrated\" = false", StandardCharsets.UTF_8.toString());
		} catch (UnsupportedEncodingException e) {
			logger.error("Error while encoding a part of URL ", e );
		}
		
		/* Jira URL string building. This URL will give the Jira Testcase keys
	
		   1. For max chunk configured numbers testcases. "max_result" is the URL argument which handles this part.
		             AND
		   2. For the testcases which are not migrated already. It will be determined by "TestCase Migrated" field which will be false for migrated testcase in Jira  
		              AND
		   3. For the testcases which need to be migrated. It will be determined by "Migrate Test to Rally" field which will be true for non-migated testcase in Jira
					  AND
		   4. For the testcases associated with the configured Jira project key */
		
		String url = jiraBaseURL + "/rest/atm/1.0/testcase/search?fields=key&maxResults=" + max_chunk + "&query=" + encodededKey;
		logger.info("String URL to get non migrated testcase keys from Jira " + url); // Log the string URL in log file
		
		  /* Call "getJiraResponse" from Utils class. This method will return the JIRA JSON response for the given URL.
		   * Since the URL has been built such a way that to list the non migrated testcases, the output of this method would be Jira testcase keys in a JSON format */
		
		HttpEntity response = Utils.getJiraResponse(url, jiraApiKey);
	    
		if (response !=  null) {
	    	String result = null;
			try {
				// Parse the Json into string; meaning get the jira testcase keys as string value from Json
				result = EntityUtils.toString(response);
				
			// Exceptional Handling while parsing the Json response
			} catch (ParseException e) {
				logger.error("Error while parsing the Json response ", e );
				
			} catch (IOException e) {
				logger.error("Error while parsing the Json response" ,e);
				
			}
			// Add the parsed Jira testcase keys into JSON Array.
			
	    	JSONArray jsonArray = new JSONArray(result);
	    	
	    	// Iterate each keys, get the value of testcase key and add it to ArrayList
	    	for (int i = 0; i < jsonArray.length(); i++) {
                 JSONObject jsonObject = jsonArray.getJSONObject(i);
                 String key = jsonObject.getString("key");                
                 logger.info("Key retrieved: " + jsonObject.getString("key"));
                 testCaseKeys.add(key);
             }
	    	 
	    } else {
	    	// Log if null response
	    	logger.error("Response is NULL from while retrieving non migrated keys from JIRA. Returning NULL");
	    }
		
		return testCaseKeys;
	}
	
    // Create a method to get the testcase details for a given jira testcase key. This method accepts Jira key as string argument and returns the testcase keys as Json Object for further processing
	
// Create a method to get JIRA Test case details for the current key
	

	public JsonObject getJiraTestCaseDetails(String key) {
			JsonObject jiraJson = null;
			
			// Jira URL string building. This URL will give the Jira testcase details for the given testcase key
			String url = jiraBaseURL + "/rest/atm/1.0/testcase/" + key;
			
			/* Call "getJiraResponse" from Utils class. This method will return the JIRA JSON response for the given URL.
			 * Since the URL has been built such a way that to get the testcase details for the given testcase key, the output of this method would be Jira Testcase details in a JSON format */
			
			HttpEntity response = Utils.getJiraResponse(url, jiraApiKey);
			if ( response !=  null) {
				try {
					// Convert the response as String and then parse the string, Return the testcase details as JsonObject
					String responseBody = EntityUtils.toString(response);
					logger.info("Testcase details for the key" + key + ": " + responseBody);
					jiraJson = JsonParser.parseString(responseBody).getAsJsonObject();
					System.out.println(jiraJson);
				
				// Exception handling
				} catch (ParseException e) {
					logger.error("Failed to retrieving JIRA testcase details for the key " + key + "; Parser exception " , e);
					
				} catch (IOException e) {
					logger.error("Failed to retrieving JIRA testcase details for the key " + key + "; IO exception " , e);
				}
				
				
			} else {
				
				logger.error("failed to get jira testcase details for the key " + key + "; Returning null");
			}

		return jiraJson;

	}
	
	
	
	// create a method to download attachments. Future implementation
	
	
	
	
	
	
	
	

}
