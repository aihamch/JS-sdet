package com.optum.coe.automation.rally;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rallydev.rest.RallyRestApi;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class RunnerClass {
    private static final Logger logger = LogManager.getLogger();

    public static void main(String[] args) {
        try {
            Gson gson = new Gson();
            JiraTestCase jiraTestCase = new JiraTestCase();
            JiraOperation jiraOperation = new JiraOperation();
            ArrayList<String> testcaseKeys = jiraOperation.getJiraNonMigratedTestcaseKeys();

            String rallyUrl = ConfigLoader.getConfigValue("RALLY_BASE_URL");
            String rallyApiKey = ConfigLoader.getConfigValue("RALLY_API_KEY");
            RallyRestApi rallyRestApi = new RallyRestApi(new URI(rallyUrl), rallyApiKey);

            for (String testcaseKey : testcaseKeys) {
                boolean rallyTestcaseCreationStatus = false;
                boolean rallyOverallTestStepAttachmentsStatus = false;
                jiraTestCase.setKey(testcaseKey);
                logger.info("Processing " + jiraTestCase.getKey());
                JsonObject jiraTestcaseJson = jiraOperation.getJiraTestCaseDetails(jiraTestCase.getKey());
                RallyOperation rallyOperation = new RallyOperation();
                String rallyTestcaseOID = rallyOperation.createRallyTestcase(jiraTestcaseJson);

                if (rallyTestcaseOID != null) {
                    rallyTestcaseCreationStatus = true;
                } else {
                    logger.error("Testcase is not created in Rally for the key " + jiraTestCase.getKey());
                    break;
                }

                // Download attachments at the testcase level
                List<String> fileAttachmentDownloadPathsTestcaseLevel = jiraOperation.jiraAttachmentsDownload(jiraTestCase.getKey(), "testcase", "file");
                if (fileAttachmentDownloadPathsTestcaseLevel != null) {
                    logger.info("Attachment paths are found in the list.");
                    List<String> testcaseAttachmentOIDs = rallyOperation.attachFilestoRallyTestcase(rallyTestcaseOID, fileAttachmentDownloadPathsTestcaseLevel);
                    Utils.deleteAttachmentFileFromLocal(fileAttachmentDownloadPathsTestcaseLevel);

                    if (testcaseAttachmentOIDs.isEmpty()) {
                        logger.error("The Jira testcase is not created in rally. Jira Testcase key " + jiraTestCase.getKey() + " is not created in rally");
                        return;
                    }
                } else {
                    logger.info("No Attachment path found for Testcase level.");
                }

                // Handle test steps
                JsonArray stepsArray = jiraTestcaseJson.getAsJsonObject("testScript").getAsJsonArray("steps");
                List<JiraTestStep> testSteps = new ArrayList<>();

                for (JsonElement element : stepsArray) {
                    JiraTestStep step = gson.fromJson(element, JiraTestStep.class);
                    testSteps.add(step);
                }

                rallyOperation.migrateTestSteps(rallyTestcaseOID, testSteps, rallyRestApi);

                // Download and upload attachments for each test step
                for (JiraTestStep step : testSteps) {
                    List<String> stepAttachmentPaths = jiraOperation.downloadStepAttachments(step);
                    List<String> embeddedImages = jiraOperation.downloadEmbeddedImages(step);
                    stepAttachmentPaths.addAll(embeddedImages);

                    if (!stepAttachmentPaths.isEmpty()) {
                        rallyOperation.attachFilesToTestStep(rallyTestcaseOID, step.getIndex(), stepAttachmentPaths);
                        Utils.deleteAttachmentFileFromLocal(stepAttachmentPaths);
                    }
                }

                if (rallyTestcaseCreationStatus && rallyOverallTestStepAttachmentsStatus) {
                    System.out.println("Rally Testcase Creation Status is true");
                } else {
                    logger.error("The Jira testcase is not created in rally. Jira Testcase key " + jiraTestCase.getKey() + " is not created in rally");
                }
            }
        } catch (Exception e) {
            logger.error("Error occurred during the migration process", e);
        }
    }
}
