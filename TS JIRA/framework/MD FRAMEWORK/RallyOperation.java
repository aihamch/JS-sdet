package com.optum.coe.automation.rally;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.JsonArray;
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

    // Method to create a Rally test case
    public String createRallyTestcase(JsonObject jiraTestcaseJson) {
        String rallyTestcaseOID = null;
        try {
            RallyRestApi restApi = new RallyRestApi(new URI(rallyBaseURL), rallyApiKey);
            restApi.setApplicationName("CreateTestCaseApp");

            // Create a new test case
            JsonObject testCase = new JsonObject();
            testCase.addProperty("Project", rallyProjectKey);

            // Add name
            testCase.addProperty("Name", Utils.getJsonString(jiraTestcaseJson, "name"));

            // Add method
            testCase.addProperty("Method", "Manual"); // Assuming a default value

            // Add priority
            String priority = Utils.getJsonString(jiraTestcaseJson, "priority");
            if (priority.equalsIgnoreCase("Requires Triage")) {
                testCase.addProperty("Priority", "Useful");
            } else {
                testCase.addProperty("Priority", priority.isEmpty() ? "Default Priority" : priority);
            }

            // Add owner
            testCase.addProperty("Owner", rallyUser);

            // Add status
            String status = Utils.getJsonString(jiraTestcaseJson, "status");
            testCase.addProperty("Ready", status.equalsIgnoreCase("Ready"));

            // Handle tags
            JsonArray tagsArray = new JsonArray();
            JsonArray labelsArray = jiraTestcaseJson.getAsJsonArray("labels");
            if (labelsArray != null) {
                for (int i = 0; i < labelsArray.size(); i++) {
                    JsonObject tagObject = Utils.findOrCreateTag(restApi, labelsArray.get(i).getAsString());
                    if (tagObject != null) {
                        JsonObject tagRefObject = new JsonObject();
                        tagRefObject.addProperty("_ref", tagObject.get("_ref").getAsString());
                        tagsArray.add(tagRefObject);
                    }
                }
            }
            testCase.add("Tags", tagsArray);

            // Handle folders
            String folderPath = Utils.getJsonString(jiraTestcaseJson, "testFolder");
            String[] folderHierarchy = folderPath.split("/");
            JsonObject testFolder = Utils.createTestFolder(folderHierarchy, rallyProjectKey, rallyBaseURL, rallyApiKey);
            if (testFolder != null) {
                testCase.add("TestFolder", testFolder);
            }

            // Create Rally test case
            CreateRequest createRequest = new CreateRequest("testcase", testCase);
            CreateResponse createResponse = restApi.create(createRequest);

            if (createResponse.wasSuccessful()) {
                rallyTestcaseOID = createResponse.getObject().get("_ref").getAsString();
                logger.info("Successfully created Rally test case with OID: " + rallyTestcaseOID);
            } else {
                logger.error("Error occurred creating Rally test case:");
                for (String error : createResponse.getErrors()) {
                    logger.error(error);
                }
            }

            restApi.close();
        } catch (Exception e) {
            logger.error("Exception while creating Rally test case: ", e);
        }

        return rallyTestcaseOID;
    }

    // Method to attach files to a Rally test case
    public List<String> attachFilestoRallyTestcase(String rallyTestcaseOID, List<String> fileAttachmentPaths) {
        List<String> attachmentOIDs = new ArrayList<>();
        try {
            RallyRestApi restApi = new RallyRestApi(new URI(rallyBaseURL), rallyApiKey);
            restApi.setApplicationName("AttachFilesToTestCaseApp");

            for (String filePath : fileAttachmentPaths) {
                File file = new File(filePath);
                byte[] fileContent = Files.readAllBytes(file.toPath());
                String fileContentBase64 = Base64.getEncoder().encodeToString(fileContent);

                JsonObject attachment = new JsonObject();
                attachment.addProperty("TestCase", rallyTestcaseOID);
                attachment.addProperty("Content", fileContentBase64);
                attachment.addProperty("ContentType", "application/octet-stream");
                attachment.addProperty("Name", file.getName());

                CreateRequest createRequest = new CreateRequest("Attachment", attachment);
                CreateResponse createResponse = restApi.create(createRequest);

                if (createResponse.wasSuccessful()) {
                    attachmentOIDs.add(createResponse.getObject().get("_ref").getAsString());
                    logger.info("Successfully uploaded attachment: " + file.getName());
                } else {
                    logger.error("Error occurred uploading attachment: " + file.getName());
                    for (String error : createResponse.getErrors()) {
                        logger.error(error);
                    }
                }
            }

            restApi.close();
        } catch (Exception e) {
            logger.error("Exception while attaching files to Rally test case: ", e);
        }

        return attachmentOIDs;
    }

    // Method to migrate test steps and their attachments
    public void migrateTestSteps(String rallyTestcaseOID, List<JiraTestStep> jiraTestSteps, RallyRestApi restApi) {
        for (JiraTestStep step : jiraTestSteps) {
            JsonObject testStep = new JsonObject();
            testStep.addProperty("TestCase", rallyTestcaseOID);
            testStep.addProperty("Index", step.getIndex());
            testStep.addProperty("Description", step.getDescription());
            testStep.addProperty("ExpectedResult", step.getExpectedResult());

            CreateRequest createRequest = new CreateRequest("TestCaseStep", testStep);
            try {
                CreateResponse createResponse = restApi.create(createRequest);
                if (createResponse.wasSuccessful()) {
                    String rallyTestStepOID = createResponse.getObject().get("_ref").getAsString();
                    logger.info("Successfully created Rally test step with OID: " + rallyTestStepOID);

                    // Attach files to the Rally test step
                    List<String> attachmentPaths = JiraOperation.downloadStepAttachments(step);
                    if (!attachmentPaths.isEmpty()) {
                        attachFilestoRallyTestStep(rallyTestStepOID, attachmentPaths);
                        Utils.deleteAttachmentFileFromLocal(attachmentPaths);
                    }
                } else {
                    logger.error("Error occurred creating Rally test step:");
                    for (String error : createResponse.getErrors()) {
                        logger.error(error);
                    }
                }
            } catch (IOException e) {
                logger.error("Exception while creating Rally test step: ", e);
            }
        }
    }

    // Method to attach files to a Rally test step
    public List<String> attachFilestoRallyTestStep(String rallyTestStepOID, List<String> fileAttachmentPaths) {
        List<String> attachmentOIDs = new ArrayList<>();
        try {
            RallyRestApi restApi = new RallyRestApi(new URI(rallyBaseURL), rallyApiKey);
            restApi.setApplicationName("AttachFilesToTestStepApp");

            for (String filePath : fileAttachmentPaths) {
                File file = new File(filePath);
                byte[] fileContent = Files.readAllBytes(file.toPath());
                String fileContentBase64 = Base64.getEncoder().encodeToString(fileContent);

                JsonObject attachment = new JsonObject();
                attachment.addProperty("TestCaseStep", rallyTestStepOID);
                attachment.addProperty("Content", fileContentBase64);
                attachment.addProperty("ContentType", "application/octet-stream");
                attachment.addProperty("Name", file.getName());

                CreateRequest createRequest = new CreateRequest("Attachment", attachment);
                CreateResponse createResponse = restApi.create(createRequest);

                if (createResponse.wasSuccessful()) {
                    attachmentOIDs.add(createResponse.getObject().get("_ref").getAsString());
                    logger.info("Successfully uploaded attachment: " + file.getName());
                } else {
                    logger.error("Error occurred uploading attachment: " + file.getName());
                    for (String error : createResponse.getErrors()) {
                        logger.error(error);
                    }
                }
            }

            restApi.close();
        } catch (Exception e) {
            logger.error("Exception while attaching files to Rally test step: ", e);
        }

        return attachmentOIDs;
    }
}
