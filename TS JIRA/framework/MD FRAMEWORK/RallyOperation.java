package com.optum.coe.automation.rally;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rallydev.rest.RallyRestApi;
import com.rallydev.rest.request.CreateRequest;
import com.rallydev.rest.response.CreateResponse;
import com.rallydev.rest.util.Fetch;
import com.rallydev.rest.util.QueryFilter;
import com.rallydev.rest.request.QueryRequest;
import com.rallydev.rest.response.QueryResponse;

public class RallyOperation {

    // Class member variables initialized from the config file
    private String rallyBaseURL;
    private String rallyApiKey;
    private String rallyProjectKey;
    private String rallyUser;

    // Logger Initialization
    private static final Logger logger = LogManager.getLogger();

    // Constructor loading configuration values
    public RallyOperation() {
        rallyBaseURL = ConfigLoader.getConfigValue("RALLY_BASE_URL");
        rallyApiKey = ConfigLoader.getConfigValue("RALLY_API_KEY");
        rallyProjectKey = ConfigLoader.getConfigValue("RALLY_PROJECT_REF");
        rallyUser = ConfigLoader.getConfigValue("RALLY_USER_REF");
        logger.info("Rally values for the project key " + rallyProjectKey + " are assigned from rally_migration_config.properties file");
    }

    // Method to create a Rally testcase using a Jira testcase JSON object
    public String createRallyTestcase(JsonObject jiraTestcaseJson) {
        RallyRestApi rallyRestApi = null;
        String rallyTestcaseOID = null;

        try {
            rallyRestApi = new RallyRestApi(new URI(rallyBaseURL), rallyApiKey);
            JsonObject newTestCase = new JsonObject();

            // Populate newTestCase with necessary fields from jiraTestcaseJson
            newTestCase.addProperty("Name", jiraTestcaseJson.get("name").getAsString());
            newTestCase.addProperty("Project", rallyProjectKey);
            newTestCase.addProperty("Owner", rallyUser);
            newTestCase.addProperty("Priority", jiraTestcaseJson.get("priority").getAsString());
            newTestCase.addProperty("Description", jiraTestcaseJson.get("objective").getAsString());

            // Create Rally TestCase
            CreateRequest createRequest = new CreateRequest("TestCase", newTestCase);
            CreateResponse createResponse = rallyRestApi.create(createRequest);

            if (createResponse.wasSuccessful()) {
                rallyTestcaseOID = createResponse.getObject().get("_ref").getAsString();
                logger.info("Successfully created test case in Rally with OID: " + rallyTestcaseOID);
            } else {
                logger.error("Error creating Rally test case. Errors: " + String.join(", ", createResponse.getErrors()));
            }
        } catch (Exception e) {
            logger.error("Exception while creating Rally test case: ", e);
        } finally {
            if (rallyRestApi != null) {
                try {
                    rallyRestApi.close();
                } catch (Exception e) {
                    logger.error("Error closing Rally connection: ", e);
                }
            }
        }
        return rallyTestcaseOID;
    }

    // Method to migrate test steps from Jira to Rally
    public void migrateTestSteps(String rallyTestcaseOID, List<JiraTestStep> jiraTestSteps, RallyRestApi rallyRestApi) {
        for (JiraTestStep step : jiraTestSteps) {
            JsonObject newTestStep = new JsonObject();
            newTestStep.addProperty("TestCase", rallyTestcaseOID);
            newTestStep.addProperty("StepIndex", step.getIndex());
            newTestStep.addProperty("Input", step.getDescription());
            newTestStep.addProperty("ExpectedResult", step.getExpectedResult());

            try {
                CreateRequest createStepRequest = new CreateRequest("TestCaseStep", newTestStep);
                CreateResponse createStepResponse = rallyRestApi.create(createStepRequest);

                if (createStepResponse.wasSuccessful()) {
                    String rallyTestStepOID = createStepResponse.getObject().get("_ref").getAsString();
                    logger.info("Successfully created test step in Rally with OID: " + rallyTestStepOID);
                } else {
                    logger.error("Error creating Rally test step. Errors: " + String.join(", ", createStepResponse.getErrors()));
                }
            } catch (Exception e) {
                logger.error("Exception while creating Rally test step: ", e);
            }
        }
    }

    // Method to attach files to a Rally testcase
    public List<String> attachFilestoRallyTestcase(String rallyTestcaseOID, List<String> filePaths) {
        List<String> attachmentOIDs = new ArrayList<>();
        RallyRestApi rallyRestApi = null;

        try {
            rallyRestApi = new RallyRestApi(new URI(rallyBaseURL), rallyApiKey);

            for (String filePath : filePaths) {
                File file = new File(filePath);
                byte[] fileContent = Files.readAllBytes(file.toPath());
                String encodedContent = Base64.getEncoder().encodeToString(fileContent);

                JsonObject newAttachment = new JsonObject();
                newAttachment.addProperty("TestCase", rallyTestcaseOID);
                newAttachment.addProperty("Content", encodedContent);
                newAttachment.addProperty("Name", file.getName());
                newAttachment.addProperty("ContentType", Files.probeContentType(file.toPath()));

                CreateRequest attachmentRequest = new CreateRequest("Attachment", newAttachment);
                CreateResponse attachmentResponse = rallyRestApi.create(attachmentRequest);

                if (attachmentResponse.wasSuccessful()) {
                    String attachmentOID = attachmentResponse.getObject().get("_ref").getAsString();
                    attachmentOIDs.add(attachmentOID);
                    logger.info("Successfully uploaded attachment: " + filePath + " to Rally testcase.");
                } else {
                    logger.error("Error uploading attachment: " + filePath + " to Rally. Errors: " + String.join(", ", attachmentResponse.getErrors()));
                }
            }
        } catch (Exception e) {
            logger.error("Exception while attaching files to Rally testcase: ", e);
        } finally {
            if (rallyRestApi != null) {
                try {
                    rallyRestApi.close();
                } catch (Exception e) {
                    logger.error("Error closing Rally connection: ", e);
                }
            }
        }
        return attachmentOIDs;
    }

    // Method to attach files to a Rally test step
    public List<String> attachFilestoRallyTestStep(String rallyTestStepOID, List<String> filePaths) {
        List<String> attachmentOIDs = new ArrayList<>();
        RallyRestApi rallyRestApi = null;

        try {
            rallyRestApi = new RallyRestApi(new URI(rallyBaseURL), rallyApiKey);

            for (String filePath : filePaths) {
                File file = new File(filePath);
                byte[] fileContent = Files.readAllBytes(file.toPath());
                String encodedContent = Base64.getEncoder().encodeToString(fileContent);

                JsonObject newAttachment = new JsonObject();
                newAttachment.addProperty("TestStep", rallyTestStepOID);
                newAttachment.addProperty("Content", encodedContent);
                newAttachment.addProperty("Name", file.getName());
                newAttachment.addProperty("ContentType", Files.probeContentType(file.toPath()));

                CreateRequest attachmentRequest = new CreateRequest("Attachment", newAttachment);
                CreateResponse attachmentResponse = rallyRestApi.create(attachmentRequest);

                if (attachmentResponse.wasSuccessful()) {
                    String attachmentOID = attachmentResponse.getObject().get("_ref").getAsString();
                    attachmentOIDs.add(attachmentOID);
                    logger.info("Successfully uploaded attachment: " + filePath + " to Rally test step.");
                } else {
                    logger.error("Error uploading attachment: " + filePath + " to Rally. Errors: " + String.join(", ", attachmentResponse.getErrors()));
                }
            }
        } catch (Exception e) {
            logger.error("Exception while attaching files to Rally test step: ", e);
        } finally {
            if (rallyRestApi != null) {
                try {
                    rallyRestApi.close();
                } catch (Exception e) {
                    logger.error("Error closing Rally connection: ", e);
                }
            }
        }
        return attachmentOIDs;
    }
}
