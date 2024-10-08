package com.optum.coe.automation.rally;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rallydev.rest.RallyRestApi;
import com.rallydev.rest.request.CreateRequest;
import com.rallydev.rest.response.CreateResponse;
import com.rallydev.rest.util.Ref;

public class RallyOperation {
	
private String RallyTestCaseOID;


	public String getRallyTestCaseOID() {
		return RallyTestCaseOID;
	}
	
	public void setRallyTestCaseOID(String RallyTestCaseOID) {
		 this.RallyTestCaseOID=RallyTestCaseOID;
	}
	

	// Initialization of the class member variables. This section can be updated in future if more member variables are added during integration if needed. 
	private String rallyBaseURL;
	private String rallyApiKey;
	private String rallyProjectKey;
	private String rallyUser;

	// Logger Initialization for RallyOperation Class
	private static final Logger logger = LogManager.getLogger();
	
	/* A Constructor loads the value from .properties file. These value will be loaded as soon as a object is created for this class.
	 * Rally Base URL, Rally API Key, Rally Project Reference, Rally user are loaded from .properties file */
	
public RallyOperation() {
	
		rallyBaseURL=ConfigLoader.getConfigValue("RALLY_BASE_URL");
		rallyApiKey=ConfigLoader.getConfigValue("RALLY_API_KEY");
		rallyProjectKey=ConfigLoader.getConfigValue("RALLY_PROJECT_REF");
		rallyUser=ConfigLoader.getConfigValue("RALLY_USER_REF");
		logger.info("Rally values for the project key " + rallyProjectKey +" are assiged from rally_migration_config.properties file");
		logger.log(Level.getLevel("VERBOSE"), "Below the values assigned from rally_migration_config.properties file. \nRally Base URL - " + rallyBaseURL + "\nRally Project Reference " + rallyProjectKey);

	}
	
	
	/* A method which handles below major functionalities
	 * 1. Transform the Jira Testcase details to Rally Test variable
	 * 2. Check if the Jira folder structure is available in Rally
	 *      a. If folder structure is not available in rally, then create the same Jira folder structure in rally for the testcase 
	 *      b. If folder structure is available in rally, no action is required 
	 * 3. Create the testcase
	 * 4. Log the testcase OID to the log file; This OID will be used while integrating TestStep, Attachments implementation for the testcase
	 * 5. Returns a boolean value as true for a successful testcase creation in rally, else returns false */
	
	
	public boolean createRallyTestcase(JsonObject jiraJson)  {
		boolean status=false;
		
		// Create JSON object for rally Json and add rally project reference key
        JsonObject rallyJson = new JsonObject();
        rallyJson.addProperty("projectRef", rallyProjectKey);
        
        // Create JSON object for testcase Json to add the rally testcase property
        JsonObject testCase = new JsonObject();
       
        // Get testcase name from Jira Json body and add it to rally testcase as a property
        JsonElement nameElement = jiraJson.get("name");
        if (nameElement != null && !nameElement.isJsonNull()) {
            testCase.addProperty("Name", nameElement.getAsString());
        } else {
            testCase.addProperty("Name", "Default Test Case Name - No TestCase name found in Jira"); // Give a default value if no name is found in Jira Json body
        }

        // Add method as Manual to rally testcase as a property
        testCase.addProperty("Method", "Manual");

        // Add priority value from Jira Json body to rally testcase as a property
        JsonElement priorityElement = jiraJson.get("priority");
        if (priorityElement != null && !priorityElement.isJsonNull()) {
            String priority = priorityElement.getAsString();
            if (priority.equalsIgnoreCase("Normal")) {
                testCase.addProperty("Priority", "Useful");
            } else {
                testCase.addProperty("Priority", priority);
            }
        } else {
            testCase.addProperty("Priority", "Default Priority"); // Add default value
        }

        // Add owner from config file to the rally testcase as a property
        testCase.addProperty("Owner", "/user/" + rallyUser);

        // Add status from Jira Json and add to the rally testcase as a property 
        JsonElement statusElement = jiraJson.get("status");
        if (statusElement != null && !statusElement.isJsonNull()) {
            testCase.addProperty("Ready", statusElement.getAsString().equals("Ready"));
        } else {
            testCase.addProperty("Ready", false); // default value
        }

        // Add tags - Need implementation as discussed in code review meeting
        JsonArray tagsArray = new JsonArray();
        JsonObject tagObject = new JsonObject();
        tagObject.addProperty("_ref", "/tag/56011614555");
        tagObject.addProperty("Name", "Billing and Eligibility");
        tagsArray.add(tagObject);
        testCase.add("Tags", tagsArray);

        // Get folder hierarchy from JIRA response
        String folderPath = jiraJson.get("folder").getAsString();
        String[] folderHierarchy = folderPath.split("/");

        // Call a Util method to create test folder based on folder hierarchy
        JsonObject testFolder = Utils.createTestFolder(folderHierarchy, rallyProjectKey , rallyBaseURL, rallyApiKey);
        if (testFolder == null) {
        	
        	logger.error("Failed to create or retrieve TestFolder during Testcase Creation process in Rally");
            //return status;// Exit the method or handle accordingly
        } else logger.info("Folder " + folderPath + " is  created successfully in Rally");
        
        // Add TestFolder to the test case
        rallyJson.add("TestFolder", testFolder);

        // Add the testCase object to the rallyJson
        rallyJson.add("testCase", testCase);

        // Save the transformed JSON to a variable
        String rallyJsonString = new GsonBuilder().setPrettyPrinting().create().toJson(rallyJson);

        //System.out.println("Transformed JSON: " + rallyJsonString);

        // Initialize Rally API
        RallyRestApi restApi = null;
		try {
			restApi = new RallyRestApi(new URI(rallyBaseURL), rallyApiKey);
		} catch (URISyntaxException e) {
			logger.error("URI Sytntax error for the URL " + rallyBaseURL + ". Please check the URL." , e);
		}
        restApi.setApplicationName("CreateTestCaseApp");

        JsonObject jsonData = JsonParser.parseString(rallyJsonString).getAsJsonObject();

        // Extract values from JSON:
        JsonObject testCaseData = jsonData.getAsJsonObject("testCase");
        JsonObject testFolderData = jsonData.getAsJsonObject("TestFolder");

        try {
            // Create a new test case
            JsonObject newTestCase = new JsonObject();
            newTestCase.addProperty("Name", testCaseData.get("Name").getAsString());
            newTestCase.addProperty("Project", rallyProjectKey);
            newTestCase.addProperty("Method", testCaseData.get("Method").getAsString());
            newTestCase.addProperty("Priority", testCaseData.get("Priority").getAsString());
            newTestCase.addProperty("Owner", testCaseData.get("Owner").getAsString());
            newTestCase.addProperty("Ready", testCaseData.get("Ready").getAsBoolean());

            // Add Tags to the test case from the JSON file
            JsonArray tagsArrayFromJson = testCaseData.getAsJsonArray("Tags");
            if (tagsArrayFromJson != null) {
                JsonArray newTagsArray = new JsonArray();
                for (JsonElement tagElement : tagsArrayFromJson) {
                    JsonObject tagObjectFromJson = tagElement.getAsJsonObject();
                    String tagRef = tagObjectFromJson.get("_ref").getAsString();
                    JsonObject newTagObject = new JsonObject();
                    newTagObject.addProperty("_ref", tagRef);
                    newTagsArray.add(newTagObject);
                }
                newTestCase.add("Tags", newTagsArray);
            }

            // Add TestFolder to the test case from the JSON file
            String testFolderRef = testFolderData.get("_ref").getAsString();
            newTestCase.addProperty("TestFolder", testFolderRef);

            CreateRequest createRequest = new CreateRequest("testcase", newTestCase);
            CreateResponse createResponse = null;
			try {
				createResponse = restApi.create(createRequest);
			} catch (IOException e) {
				logger.error("IO exception during create request." , e);
			}

            if (createResponse.wasSuccessful()) {
            	status = true;
            	 RallyTestCaseOID=createResponse.getObject().get("_ref").getAsString();
            	logger.info("Successfully created test case and the OID for created testcase: " + createResponse.getObject().get("_ref").getAsString());
             	
            } else {
            	logger.error("Error occurred creating test case");	
                for (String error : createResponse.getErrors()) {
                	logger.error(error);
                }
            }
            
            
        } finally {
            // Release resources
            try {
				restApi.close();
				logger.info("Closed rest api resource in finally block");
			} catch (IOException e) {
				logger.error("Error occurred while closing rest api resource at finally block" , e);
			}
            
        }
		return status;
                 
         
    }
	public boolean createTestStep(JiraTestStep steps) throws IOException, URISyntaxException {
		
		JsonObject payLoad = new JsonObject();
		 payLoad.addProperty("TestCase", RallyTestCaseOID);
		 payLoad.addProperty("Input",processInput(steps));
		 payLoad.addProperty("expectedResult",steps.getExpectedresult());
		 payLoad.addProperty("index", steps.getIndex());
		 payLoad.addProperty("id", steps.getId());
		 if(steps.getTestdata() !=null) {
			 payLoad.addProperty("Input", processInput(steps)); 
		 }
		
		 boolean status= false;
		
		 
	        String rallyJsonString = new GsonBuilder().setPrettyPrinting().create().toJson(payLoad);
	 
	        System.out.println("Transformed JSON: " + rallyJsonString);
	        
	        JsonParser.parseString(rallyJsonString).getAsJsonObject();
	        
	        
	       RallyRestApi restApi = new RallyRestApi(new URI(rallyBaseURL), rallyApiKey);
	        restApi.setApplicationName("CreateTestCaseStepApp");
	        

	        try{CreateRequest createRequest = new CreateRequest("testcasestep",payLoad );
	            CreateResponse createResponse = restApi.create(createRequest);
	 
	            if (createResponse.wasSuccessful()) {
	            	String TestcaseStepOID=Ref.getRelativeRef(createResponse.getObject().get("_ref").getAsString());
	                logger.info("Successfully created test Step and the OID for created teststep: " + createResponse.getObject().get("_ref").getAsString());
	            } else {
	                logger.error("Error occurred creating test step");
	                for (String error : createResponse.getErrors()) {
	                    logger.error(error);
	                }
	            }
	        } finally
	        {
	            try {
				restApi.close();
				logger.info("Closed rest api resource in finally block");
			} catch (IOException e) {
				logger.error("Error occurred while closing rest api resource at finally block" , e);
			}
	        } 
	       
	   
		return status;
	}

		private static String processInput(JiraTestStep step) {
			if(step.getTestdata()!=null && !step.getTestdata().isEmpty()) {
				return step.getDescription() +  " | " + step.getTestdata();
			}else {
				return step.getDescription();
			}
		}
	
}

	
	
	
	
	
	
	
