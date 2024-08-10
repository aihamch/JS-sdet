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
import org.json.JSONObject;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rallydev.rest.RallyRestApi;
import com.rallydev.rest.request.CreateRequest;
import com.rallydev.rest.response.CreateResponse;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RallyOperation {

    // Initialization of Logger for RallyOperation Class
    private static final Logger logger = LogManager.getLogger();

    // Initialization of Rally related properties.
    private String rallyBaseURL;
    private String rallyApiKey;
    private String rallyProjectKey;
    private String rallyUser;
   
    /*
     * A Constructor loads the value from .properties file. These values will be
     * loaded as soon as an object is created for this class. Rally Base URL and
     * Rally API Key values are loaded from .properties file.
     */
    public RallyOperation() {
        rallyBaseURL = ConfigLoader.getConfigValue("RALLY_BASE_URL");
        rallyApiKey = ConfigLoader.getConfigValue("RALLY_API_KEY");
        rallyProjectKey = ConfigLoader.getConfigValue("RALLY_PROJECT_REF");
        rallyUser = ConfigLoader.getConfigValue("RALLY_USER_REF");
        logger.info("Rally values for the project key " + rallyProjectKey + " are assigned from rally_migration_config.properties file");
        logger.log(Level.getLevel("VERBOSE"), "Below the values assigned from rally_migration_config.properties file. \nRally Base URL - " + rallyBaseURL + "\nRally Project Reference " + rallyProjectKey);
    }



    /*
     * Create method to create a new Rally Test case and returns the Test case OID
     * if created successfully A Json Object is passed to this method which contains
     * the Jira Testcase details which is required to create the Rally Testcase
     */
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

        testCase.addProperty("Owner", "/user/" + name);

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
		return rallyTestCaseOID;
        }

    /*
     * Method to attach files to a Rally Test Case Test case OID and List of file
     * paths are given as input arguments
     */
    public List<String> attachFilestoRallyTestcase(String rallyTestCaseOID, List<String> filePaths) {
        List<String> testcaseAttachmentOIDs = new ArrayList<String>();
        RallyRestApi rallyApi = null;

        try {
            // Open Rally API connection
            rallyApi = new RallyRestApi(new URI(rallyBaseURL), rallyApiKey);
        } catch (URISyntaxException e) {
            logger.error("Rally Base URL has some syntax error.", e);
        }

        try {
            for (String filePath : filePaths) {
                try {
                    // Call method to attach the file and return the OID of the attachment in Rally
                    testcaseAttachmentOIDs.add(attachFileToRallyTestcase(rallyApi, rallyTestCaseOID, filePath));
                    logger.info("File " + filePath + " is attached for the testcase OID " + rallyTestCaseOID
                            + " in Rally successfully");
                } catch (IOException e) {
                    testcaseAttachmentOIDs.clear();
                    logger.error("File " + filePath + " is not attached to Rally due to IO exception.", e);
                    return testcaseAttachmentOIDs;
                }
            }
        } finally {
            try {
                rallyApi.close();
            } catch (IOException e) {
                logger.error("Rally API resource is not closed due to IO exception.", e);
            }
        }

        return testcaseAttachmentOIDs;
    }

    /*
     * Method to migrate test steps from JIRA to Rally
     */
    public void migrateTestSteps(String rallyTestCaseRef, List<JiraTestStep> jiraTestSteps, RallyRestApi rallyRestApi) {
        // Reverse the Jira test steps order
        Collections.reverse(jiraTestSteps);

        for (JiraTestStep step : jiraTestSteps) {
            try {
                // Create a new Json object for each test step
                JsonObject newTestStep = new JsonObject();
                newTestStep.addProperty("TestCase", rallyTestCaseRef);
                newTestStep.addProperty("StepIndex", step.getIndex());
                newTestStep.addProperty("Input", step.getDescription());
                newTestStep.addProperty("ExpectedResult", step.getExpectedResult());
                newTestStep.addProperty("TestData", step.getTestData());

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

                // Create the Rally test case step
                CreateRequest createRequest = new CreateRequest("testcasestep", newTestStep);
                CreateResponse createResponse = rallyRestApi.create(createRequest);

                // Log the creation result
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

    /*
     * Method to create a Rally Test Step
     */
    public String createRallyTestStep(String rallyTestCaseOID, JsonObject gsonTestStepJson) {
        String rallyTestStepOID = null;
        RallyRestApi restApi = null;

        try {
            restApi = new RallyRestApi(new URI(rallyBaseURL), rallyApiKey);

            // Construct the JsonObject for the test step
            JsonObject newTestStep = new JsonObject();
            newTestStep.addProperty("TestCase", rallyTestCaseOID);
            newTestStep.addProperty("StepIndex", gsonTestStepJson.get("index").getAsInt());
            newTestStep.addProperty("Input", gsonTestStepJson.get("description").getAsString());
            newTestStep.addProperty("ExpectedResult", gsonTestStepJson.get("expectedResult").getAsString());
            newTestStep.addProperty("TestData", gsonTestStepJson.get("testData").getAsString());

            CreateRequest createRequest = new CreateRequest("testcasestep", newTestStep);
            CreateResponse createResponse = restApi.create(createRequest);

            if (createResponse.wasSuccessful()) {
                rallyTestStepOID = createResponse.getObject().get("_ref").getAsString();
                logger.info("Successfully created test step: " + gsonTestStepJson.get("description").getAsString());
            } else {
                logger.error("Error occurred creating test step in Rally.");
                for (String error : createResponse.getErrors()) {
                    logger.error(error);
                }
            }
        } catch (Exception e) {
            logger.error("Exception occurred while creating test step in Rally: ", e);
        } finally {
            if (restApi != null) {
                try {
                    restApi.close();
                } catch (IOException e) {
                    logger.error("Exception occurred while closing Rally REST API: ", e);
                }
            }
        }

        return rallyTestStepOID;
    }

    /*
     * Method to attach files to a Rally Test Step
     */
    public List<String> attachFilestoRallyTestStep(String rallyTestStepOID, List<String> filePaths) {
        List<String> testStepAttachmentOIDs = new ArrayList<String>();
        RallyRestApi rallyApi = null;

        try {
            rallyApi = new RallyRestApi(new URI(rallyBaseURL), rallyApiKey);

            for (String filePath : filePaths) {
                try {
                    testStepAttachmentOIDs.add(attachFileToRallyTestcase(rallyApi, rallyTestStepOID, filePath));
                    logger.info("File " + filePath + " is attached for the test step OID " + rallyTestStepOID + " in Rally successfully");
                } catch (IOException e) {
                    testStepAttachmentOIDs.clear();
                    logger.error("File " + filePath + " is not attached to Rally due to IO exception.", e);
                    return testStepAttachmentOIDs;
                }
            }
        } catch (URISyntaxException e) {
            logger.error("Rally Base URL has some syntax error.", e);
        } finally {
            try {
                rallyApi.close();
            } catch (IOException e) {
                logger.error("Rally API resource is not closed due to IO exception.", e);
            }
        }

        return testStepAttachmentOIDs;
    }

    /*
     * Helper method to upload attachments to Rally for a specific Test Case
     */
    private List<String> uploadAttachmentsToRally(List<String> attachmentPaths, RallyRestApi rallyRestApi, String rallyTestCaseRef) {
        List<String> attachmentRefs = new ArrayList<>();

        for (String filePath : attachmentPaths) {
            try {
                // Upload each attachment and add the reference to the list
                String attachmentRef = attachFileToRallyTestcase(rallyRestApi, rallyTestCaseRef, filePath);
                if (attachmentRef != null) {
                    attachmentRefs.add(attachmentRef);
                }
            } catch (IOException e) {
                logger.error("Failed to upload attachment to Rally: " + filePath, e);
            }
        }

        return attachmentRefs;
    }

    /*
     * Helper method to attach a file to a Rally Test Case
     */
    private String attachFileToRallyTestcase(RallyRestApi rallyApi, String testCaseId, String filePath) throws IOException {
        // Step 1: Read the file and encode it in Base64
        byte[] fileContent = Files.readAllBytes(Paths.get(filePath));
        String encodedContent = Base64.getEncoder().encodeToString(fileContent);

        // Step 2: Create a new attachment content record in Rally
        JsonObject newAttachmentContent = new JsonObject();
        newAttachmentContent.addProperty("Content", encodedContent);

        CreateRequest attachmentContentCreateRequest = new CreateRequest("AttachmentContent", newAttachmentContent);
        CreateResponse attachmentContentCreateResponse = rallyApi.create(attachmentContentCreateRequest);

        if (!attachmentContentCreateResponse.wasSuccessful()) {
            logger.error("Failed to create attachment content: " + attachmentContentCreateResponse.getErrors());
            return null;
        }

        // Step 3: Create the attachment record and associate it with the test case
        JsonObject newAttachment = new JsonObject();
        newAttachment.addProperty("Artifact", testCaseId);
        newAttachment.addProperty("Content", attachmentContentCreateResponse.getObject().get("_ref").getAsString());
        newAttachment.addProperty("ContentType", "application/octet-stream");
        newAttachment.addProperty("Name", new File(filePath).getName());

        CreateRequest attachmentCreateRequest = new CreateRequest("Attachment", newAttachment);
        CreateResponse attachmentCreateResponse = rallyApi.create(attachmentCreateRequest);

        if (attachmentCreateResponse.wasSuccessful()) {
            String attachmentOID = attachmentCreateResponse.getObject().get("_ref").getAsString();
            return attachmentOID;
        } else {
            logger.error("Failed to create attachment: " + attachmentCreateResponse.getErrors());
            return null;
        }
    }
}
