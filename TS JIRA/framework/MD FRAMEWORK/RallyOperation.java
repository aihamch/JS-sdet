package com.optum.coe.automation.rally;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

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
import com.rallydev.rest.request.QueryRequest;
import com.rallydev.rest.response.CreateResponse;
import com.rallydev.rest.response.QueryResponse;
import com.rallydev.rest.util.Fetch;
import com.rallydev.rest.util.QueryFilter;

public class RallyOperation {

	private String rallyBaseURL;
	private String rallyApiKey;
	private String rallyProjectKey;
	private String rallyUser;
	private static final Logger logger = LogManager.getLogger();

	public RallyOperation() {
		rallyBaseURL = ConfigLoader.getConfigValue("RALLY_BASE_URL");
		rallyApiKey = ConfigLoader.getConfigValue("RALLY_API_KEY");
		rallyProjectKey = ConfigLoader.getConfigValue("RALLY_PROJECT_REF");
		rallyUser = ConfigLoader.getConfigValue("RALLY_USER_REF");
		logger.info("Rally values for the project key " + rallyProjectKey + " are assigned from rally_migration_config.properties file");
		logger.log(Level.getLevel("VERBOSE"), "Below the values assigned from rally_migration_config.properties file. \nRally Base URL - " + rallyBaseURL + "\nRally Project Reference " + rallyProjectKey);
	}

	public String createRallyTestcase(JsonObject jiraJson) throws IOException {
	    String rallyTestCaseOID = null;
	    JsonObject rallyJson = new JsonObject();
	    rallyJson.addProperty("projectRef", rallyProjectKey);

	    JsonObject testCase = new JsonObject();

	    // Safely retrieve the name element, with a fallback default name
	    String name = Utils.getJsonString(jiraJson, "name");
	    if (name.isEmpty()) {
	        name = "Default Test Case Name - No TestCase name found in Jira";
	    }
	    testCase.addProperty("Name", name);

	    testCase.addProperty("Method", "Manual");

	    // Safely retrieve the priority element, with a fallback default priority
	    String priority = Utils.getJsonString(jiraJson, "priority");
	    if (priority.equalsIgnoreCase("Normal")) {
	        priority = "Useful";
	    } else if (priority.isEmpty()) {
	        priority = "Default Priority";
	    }
	    testCase.addProperty("Priority", priority);

	    testCase.addProperty("Owner", "/user/" + rallyUser);

	    // Safely retrieve the status element, with a fallback ready status
	    String status = Utils.getJsonString(jiraJson, "status");
	    testCase.addProperty("Ready", status.equals("Ready"));

	    // Handle labels/tags
	    JsonArray tagsArray = new JsonArray();
	    if (jiraJson.has("labels")) {
	        JsonArray labelsArray = jiraJson.getAsJsonArray("labels");
	        RallyRestApi restApi = null;
	        try {
	            restApi = new RallyRestApi(new URI(rallyBaseURL), rallyApiKey);
	            for (JsonElement labelElement : labelsArray) {
	                String label = labelElement.getAsString();
	                JsonObject tag = Utils.findOrCreateTag(restApi, label);
	                if (tag != null) {
	                    tagsArray.add(tag);
	                }
	            }
	        } catch (URISyntaxException e) {
	            logger.error("URI Syntax error for the URL " + rallyBaseURL + ". Please check the URL.", e);
	        } finally {
	            if (restApi != null) {
	                restApi.close();
	            }
	        }
	        testCase.add("Tags", tagsArray);
	    }

	    // Handle folder creation
	    String folderPath = Utils.getJsonString(jiraJson, "folder");
	    if (!folderPath.isEmpty()) {
	        String[] folderHierarchy = folderPath.split("/");
	        JsonObject testFolder = Utils.createTestFolder(folderHierarchy, rallyProjectKey, rallyBaseURL, rallyApiKey);
	        if (testFolder == null) {
	            logger.error("Failed to create or retrieve TestFolder during Testcase Creation process in Rally");
	            return rallyTestCaseOID;
	        } else {
	            rallyJson.add("TestFolder", testFolder);
	            logger.info("Folder " + folderPath + " is created successfully in Rally");
	        }
	    }

	    rallyJson.add("testCase", testCase);
	    String rallyJsonString = new GsonBuilder().setPrettyPrinting().create().toJson(rallyJson);

	    RallyRestApi restApi = null;
	    try {
	        restApi = new RallyRestApi(new URI(rallyBaseURL), rallyApiKey);
	        restApi.setApplicationName("CreateTestCaseApp");

	        JsonObject jsonData = JsonParser.parseString(rallyJsonString).getAsJsonObject();
	        JsonObject testCaseData = jsonData.getAsJsonObject("testCase");
	        JsonObject testFolderData = jsonData.getAsJsonObject("TestFolder");

	        JsonObject newTestCase = new JsonObject();
	        newTestCase.addProperty("Name", testCaseData.get("Name").getAsString());
	        newTestCase.addProperty("Project", rallyProjectKey);
	        newTestCase.addProperty("Method", testCaseData.get("Method").getAsString());
	        newTestCase.addProperty("Priority", testCaseData.get("Priority").getAsString());
	        newTestCase.addProperty("Owner", testCaseData.get("Owner").getAsString());
	        newTestCase.addProperty("Ready", testCaseData.get("Ready").getAsBoolean());

	        if (testCaseData.has("Tags")) {
	            newTestCase.add("Tags", testCaseData.getAsJsonArray("Tags"));
	        }

	        if (testFolderData != null) {
	            String testFolderRef = testFolderData.get("_ref").getAsString();
	            newTestCase.addProperty("TestFolder", testFolderRef);
	        }

	        CreateRequest createRequest = new CreateRequest("testcase", newTestCase);
	        CreateResponse createResponse = restApi.create(createRequest);

	        if (createResponse.wasSuccessful()) {
	            rallyTestCaseOID = createResponse.getObject().get("_ref").getAsString();
	            logger.info("Successfully created test case and the OID for created testcase: " + rallyTestCaseOID);
	        } else {
	            logger.error("Error occurred creating test case");
	            for (String error : createResponse.getErrors()) {
	                logger.error(error);
	            }
	        }
	    } catch (Exception e) {
	        logger.error("Exception while creating Rally test case", e);
	    } finally {
	        if (restApi != null) {
	            try {
	                restApi.close();
	            } catch (IOException e) {
	                logger.error("Error occurred while closing RallyRestApi", e);
	            }
	        }
	    }

	    return rallyTestCaseOID;
	}
	
	
	
	
	
	
	  public String attachFileToRallyTestCase(RallyRestApi rallyApi, String testCaseId, String filePath) throws IOException {
	        // Step 1: Read the file and encode it in Base64
	        byte[] fileContent = Files.readAllBytes(Paths.get(filePath));
	        String encodedContent = Base64.getEncoder().encodeToString(fileContent);

	        String ContentType = null;
	        String attachmentType = filePath.substring(filePath.lastIndexOf('.')).toLowerCase();

	        if (attachmentType.equals("png")) {
	            ContentType = "image/png";
	        } else if (attachmentType.equals("txt")) {
	            ContentType = "text/plain";
	        } else if (attachmentType.equals("jpeg") || attachmentType.equals("jpg")) {
	            ContentType = "image/jpeg";
	        } else if (attachmentType.equals("xml")) {
	            ContentType = "application/xml";
	        } else if (attachmentType.equals("gif")) {
	            ContentType = "image/gif";
	        } else if (attachmentType.equals("doc")) {
	            ContentType = "application/msword";
	        } else if (attachmentType.equals("docx")) {
	            ContentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
	        } else if (attachmentType.equals("xls")) {
	            ContentType = "application/vnd.ms-excel";
	        } else if (attachmentType.equals("xlsx")) {
	            ContentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
	        } else if (attachmentType.equals("zip")) {
	            ContentType = "application/zip";
	        }

	        // Step 2: Create the AttachmentContent
	        JsonObject attachmentContent = new JsonObject();
	        attachmentContent.addProperty("Content", encodedContent);
	        CreateRequest attachmentContentRequest = new CreateRequest("AttachmentContent", attachmentContent);
	        CreateResponse attachmentContentResponse = rallyApi.create(attachmentContentRequest);
	        if (!attachmentContentResponse.wasSuccessful()) {
	            logger.error("Error creating AttachmentContent for file " + filePath + ": " + attachmentContentResponse.getErrors());
	            return null;
	        }
	        String attachmentContentRef = attachmentContentResponse.getObject().get("_ref").getAsString();

	        // Step 3: Create the Attachment
	        JsonObject attachment = new JsonObject();
	        attachment.addProperty("Artifact", "/testcase/" + testCaseId); // Reference to the test case
	        attachment.addProperty("Content", attachmentContentRef);
	        attachment.addProperty("Name", Paths.get(filePath).getFileName().toString());
	        attachment.addProperty("Description", "Jira to Rally Migration Automated Attachments");
	        attachment.addProperty("ContentType", ContentType);
	        attachment.addProperty("Size", fileContent.length);
	        CreateRequest attachmentRequest = new CreateRequest("Attachment", attachment);
	        CreateResponse attachmentResponse = rallyApi.create(attachmentRequest);
	        if (attachmentResponse.wasSuccessful()) {
	            logger.info("Attachment created successfully for file " + filePath + ": " + attachmentResponse.getObject().get("_ref").getAsString());
	            return attachmentResponse.getObject().get("_ref").getAsString();
	        } else {
	            logger.error("Error creating Attachment for file " + filePath + ": " + attachmentResponse.getErrors());
	            return null;
	        }
	    }

	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	

	public List<String> attachFilestoRallyTestcase(String rallyTestCaseOID, List<String> filePaths) {
	    List<String> testcaseAttachmentOIDs = new ArrayList<>();
	    RallyRestApi rallyApi = null;
	    try {
	        rallyApi = new RallyRestApi(new URI(rallyBaseURL), rallyApiKey);
	        for (String filePath : filePaths) {
	            try {
	                String attachmentOID = attachFileToRallyTestCase(rallyApi, rallyTestCaseOID, filePath);
	                if (attachmentOID != null) {
	                    testcaseAttachmentOIDs.add(attachmentOID);
	                    logger.info("File " + filePath + " is attached for the testcase OID " + rallyTestCaseOID + " in Rally successfully");
	                } else {
	                    logger.error("Failed to attach file " + filePath + " to Rally TestCase.");
	                }
	            } catch (IOException e) {
	                logger.error("File " + filePath + " is not attached to Rally due to IO exception.", e);
	            }
	        }
	    } catch (Exception e) {
	        logger.error("Error while attaching files to Rally TestCase", e);
	    } finally {
	        try {
	            if (rallyApi != null) {
	                rallyApi.close();
	            }
	        } catch (IOException e) {
	            logger.error("Rally API resource is not closed due to IO exception.", e);
	        }
	    }
	    return testcaseAttachmentOIDs;
	}

	private String getTestStepRef(RallyRestApi rallyApi, String rallyTestCaseOID, int stepIndex) throws IOException {
	    // Create a query request for the "testcasestep" endpoint
	    QueryRequest request = new QueryRequest("testcasestep");
	    request.setQueryFilter(new QueryFilter("TestCase.ObjectID", "=", rallyTestCaseOID)
	            .and(new QueryFilter("StepIndex", "=", String.valueOf(stepIndex))));
	    request.setFetch(new Fetch("ObjectID"));

	    // Execute the query and get the results
	    QueryResponse response = rallyApi.query(request);
	    
	    if (response.wasSuccessful() && response.getTotalResultCount() > 0) {
	        JsonObject queryResult = response.getResults().get(0).getAsJsonObject();
	        return queryResult.get("_ref").getAsString();
	    } else {
	        logger.error("Test step not found for TestCase OID: " + rallyTestCaseOID + " and StepIndex: " + stepIndex);
	        throw new IOException("Test step not found");
	    }
	}

	private void attachFileToRallyTestStep(RallyRestApi rallyApi, String testStepRef, String filePath) throws IOException {
	    attachFile(rallyApi, testStepRef, filePath, "testcasestep");
	}

	private String attachFile(RallyRestApi rallyApi, String rallyObjectRef, String filePath, String attachmentType) throws IOException {
	    File file = new File(filePath);
	    if (!file.exists()) {
	        logger.error("File not found: " + filePath);
	        return null;
	    }
	    String base64EncodedContent = Base64.getEncoder().encodeToString(Files.readAllBytes(file.toPath()));

	    JsonObject attachmentContent = new JsonObject();
	    attachmentContent.addProperty("Content", base64EncodedContent);
	    CreateRequest attachmentContentRequest = new CreateRequest("AttachmentContent", attachmentContent);
	    CreateResponse attachmentContentResponse = rallyApi.create(attachmentContentRequest);
	    String attachmentContentRef = attachmentContentResponse.getObject().get("_ref").getAsString();

	    JsonObject attachment = new JsonObject();
	    attachment.addProperty("Artifact", rallyObjectRef);
	    attachment.addProperty("Content", attachmentContentRef);
	    attachment.addProperty("Name", file.getName());
	    attachment.addProperty("ContentType", Files.probeContentType(file.toPath()));
	    attachment.addProperty("Size", file.length());

	    CreateRequest attachmentRequest = new CreateRequest("Attachment", attachment);
	    CreateResponse attachmentResponse = rallyApi.create(attachmentRequest);
	    return attachmentResponse.wasSuccessful() ? attachmentResponse.getObject().get("_ref").getAsString() : null;
	}

	public void attachFilesToTestStep(String rallyTestCaseOID, int stepIndex, List<String> filePaths) {
	    RallyRestApi rallyApi = null;
	    try {
	        rallyApi = new RallyRestApi(new URI(rallyBaseURL), rallyApiKey);
	        String testStepRef = getTestStepRef(rallyApi, rallyTestCaseOID, stepIndex);
	        if (testStepRef != null) {
	            for (String filePath : filePaths) {
	                try {
	                    attachFileToRallyTestStep(rallyApi, testStepRef, filePath);
	                    logger.info("File " + filePath + " is attached to the test step index " + stepIndex + " in Rally successfully");
	                } catch (IOException e) {
	                    logger.error("File " + filePath + " is not attached to Rally due to IO exception.", e);
	                }
	            }
	        } else {
	            logger.error("Test step reference not found for test case OID: " + rallyTestCaseOID + " and step index: " + stepIndex);
	        }
	    } catch (Exception e) {
	        logger.error("Error while attaching files to Rally TestStep", e);
	    } finally {
	        try {
	            if (rallyApi != null) {
	                rallyApi.close();
	            }
	        } catch (IOException e) {
	            logger.error("Rally API resource is not closed due to IO exception.", e);
	        }
	    }
	}
    // New method to upload a list of attachments to Rally
    public List<String> uploadAttachmentsToRally(List<String> attachmentPaths, RallyRestApi rallyRestApi, String rallyTestCaseRef) {
        List<String> rallyAttachmentRefs = new ArrayList<>();

        for (String filePath : attachmentPaths) {
            try {
                String attachmentRef = attachFileToRallyTestCase(rallyRestApi, rallyTestCaseRef, filePath);
                rallyAttachmentRefs.add(attachmentRef);
                logger.info("File " + filePath + " uploaded successfully to Rally.");
            } catch (IOException e) {
                logger.error("Failed to upload attachment: " + filePath, e);
            }
        }

        return rallyAttachmentRefs;
    }

	// Method to migrate test steps from JIRA to Rally
	public void migrateTestSteps(String rallyTestCaseRef, List<JiraTestStep> jiraTestSteps, RallyRestApi rallyRestApi) {
	    Collections.reverse(jiraTestSteps);

	    for (JiraTestStep step : jiraTestSteps) {
	        try {
	            JsonObject newTestStep = new JsonObject();
	            newTestStep.addProperty("TestCase", rallyTestCaseRef);
	            newTestStep.addProperty("StepIndex", step.getIndex());
	            newTestStep.addProperty("Input", step.getDescription());
	            newTestStep.addProperty("ExpectedResult", step.getExpectedresult());
	            newTestStep.addProperty("TestData", step.getTestdata());

	            // Handle attachments
	            List<String> attachmentPaths = JiraOperation.downloadStepAttachments(step);
	            List<String> rallyAttachmentRefs = uploadAttachmentsToRally(attachmentPaths, rallyRestApi, rallyTestCaseRef);

	            if (!rallyAttachmentRefs.isEmpty()) {
	                JsonArray attachmentsArray = new JsonArray();
	                for (String attachmentRef : rallyAttachmentRefs) {
	                    JsonObject attachmentObj = new JsonObject();
	                    attachmentObj.addProperty("_ref", attachmentRef);
	                    attachmentsArray.add(attachmentObj);
	                }
	                newTestStep.add("Attachments", attachmentsArray);
	            }

	            CreateRequest createRequest = new CreateRequest("testcasestep", newTestStep);
	            CreateResponse createResponse = rallyRestApi.create(createRequest);

	            if (createResponse.wasSuccessful()) {
	                logger.info("Successfully created test step: " + step.getDescription());
	            } else {
	                logger.error("Failed to create test step: " + step.getDescription() + ". Error: " + createResponse.getErrors());
	            }
	        } catch (Exception e) {
	            logger.error("Exception while creating test step: " + step.getDescription(), e);
	        }
	    }
	}
}
