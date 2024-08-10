package com.optum.coe.automation.rally;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private String maxChunk;
    private String tcFileAttachmentDownloadLocation;
    private String tsFileAttachmentDownloadLocation;

    // Logger Initialization for JiraOperation Class
    private static final Logger logger = LogManager.getLogger();

    // Constructor loads values from .properties file.
    public JiraOperation() {
        jiraBaseURL = ConfigLoader.getConfigValue("JIRA_BASE_URL");
        jiraApiKey = ConfigLoader.getConfigValue("JIRA_API_TOKEN");
        jiraProjectKey = ConfigLoader.getConfigValue("JIRA_PROJECT_KEY");
        maxChunk = ConfigLoader.getConfigValue("MAX_VALUE_CHUNK");
        tcFileAttachmentDownloadLocation = ConfigLoader.getConfigValue("TEST_CASE_FILE_ATTACHMENT_LOCATION");
        tsFileAttachmentDownloadLocation = ConfigLoader.getConfigValue("TEST_STEP_FILE_ATTACHMENT_LOCATION");

        logger.info("Jira values for the project key " + jiraProjectKey + " are assigned from rally_migration_config.properties file");
        logger.log(Level.getLevel("VERBOSE"),
                "Below the values assigned from rally_migration_config.properties file. \nJira Base URL - " + jiraBaseURL
                        + "\nJira Project Key " + jiraProjectKey + "\nMax Chunk value - " + maxChunk
                        + "\nTest Case File Attachment Download location - " + tcFileAttachmentDownloadLocation
                        + "\nTest Step File Attachment location - " + tsFileAttachmentDownloadLocation);
    }

    public ArrayList<String> getJiraNonMigratedTestcaseKeys() {
        ArrayList<String> testCaseKeys = new ArrayList<>();

        String encodedKey = null;
        try {
            encodedKey = URLEncoder.encode(
                    "projectKey = " + jiraProjectKey
                            + " AND \"Migrate Test to Rally\" = true AND \"TestCase Migrated\" = false",
                    StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            logger.error("Error while encoding a part of URL ", e);
        }

        String url = jiraBaseURL + "/rest/atm/1.0/testcase/search?fields=key&maxResults=" + maxChunk + "&query=" + encodedKey;
        logger.info("String URL to get non migrated testcase keys from Jira " + url);

        HttpEntity response = Utils.getJiraResponse(url, jiraApiKey);

        if (response != null) {
            String result = null;
            try {
                result = EntityUtils.toString(response);
            } catch (ParseException | IOException e) {
                logger.error("Error while parsing the Json response", e);
            }
            JSONArray jsonArray = new JSONArray(result);

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                String key = jsonObject.getString("key");
                logger.info("Key retrieved: " + key);
                testCaseKeys.add(key);
            }

        } else {
            logger.error("Response is NULL from while retrieving non migrated keys from JIRA. Returning NULL");
        }

        return testCaseKeys;
    }

    public JsonObject getJiraTestCaseDetails(String testCaseKey) {
        String jsonResponse = getJiraResponse(testCaseKey);

        // Create a JSONObject from the response
        JSONObject jiraTestcaseJson = new JSONObject(jsonResponse);

        // Convert the JSONObject to a JsonObject using Gson
        JsonObject gsonTestcaseJson = JsonParser.parseString(jiraTestcaseJson.toString()).getAsJsonObject();

        return gsonTestcaseJson;
    }

    private String getJiraResponse(String testCaseKey) {
        String urlString = jiraBaseURL + "/rest/atm/1.0/testcase/" + testCaseKey;
        HttpURLConnection connection = null;
        StringBuilder response = new StringBuilder();

        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + jiraApiKey);
            connection.setRequestProperty("Content-Type", "application/json");

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
            } else {
                logger.error("GET request to Jira failed with response code: " + responseCode);
            }
        } catch (Exception e) {
            logger.error("Exception occurred while making GET request to Jira: ", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return response.toString();
    }

    public List<String> jiraAttachmentsDownload(String testcaseKey, String testType, String attachmentType) {
        List<String> fileAttachmentDownloadPaths = null;
        String testAttachmentUrl = null;
        if (testType.equals("testcase")) {
            testAttachmentUrl = jiraBaseURL + "/rest/atm/1.0/testcase/" + testcaseKey + "/attachments";
            logger.info("URL String for testcase attachments: " + testAttachmentUrl);
        } else if (testType.equals("teststep") && attachmentType.equals("file")) {
            testAttachmentUrl = jiraBaseURL + "/rest/atm/1.0/testcase/" + testcaseKey;
            logger.info("URL String for teststep attachments: " + testAttachmentUrl);
        } else if (testType.equals("teststep") && attachmentType.equals("embedded")) {
            testAttachmentUrl = jiraBaseURL + "/rest/atm/1.0/testcase/" + testcaseKey;
            logger.info("URL String for teststep embedded attachments: " + testAttachmentUrl);
        } else {
            logger.error("Usage of jiraAttachmentsDownload is not correct. The argument value should be either testcase or teststep");
            return null;
        }

        HttpEntity response = Utils.getJiraResponse(testAttachmentUrl, jiraApiKey);
        if (response != null) {
            String result = null;
            try {
                result = EntityUtils.toString(response);
                if (result.trim().isEmpty() || result.equals("{}") || result.equals("[]")) {
                    logger.info("No Attachment URL found for the testcase key " + testcaseKey);
                } else {
                    logger.info("Attachment URL is found for the testcase key " + testcaseKey + "; JSON body while finding the attachment URL and name of the attachment: " + result);

                    if (testType.equals("testcase")) {
                        Map<String, String> testMap = Utils.pharseJsonGetAttachmentUrlAndName(result);
                        fileAttachmentDownloadPaths = Utils.downloadFileAttachmentFromJiraTestCase(testMap, tcFileAttachmentDownloadLocation, jiraApiKey, testcaseKey);
                    } else if (testType.equals("teststep") && attachmentType.equals("file")) {
                        fileAttachmentDownloadPaths = Utils.downloadFileAttachmentFromTestStep(result, jiraApiKey, tsFileAttachmentDownloadLocation, testcaseKey, jiraBaseURL);
                    } else if (testType.equals("teststep") && attachmentType.equals("embedded")) {
                        List<String> descriptionAttachmentDownloadPaths, testDataAttachmentDownloadPaths, expectedResultAttachmentDownloadPaths;
                        descriptionAttachmentDownloadPaths = Utils.downloadTestStepEmbeddedAttachments(result, jiraApiKey, tsFileAttachmentDownloadLocation, testcaseKey, jiraBaseURL, "description");
                        testDataAttachmentDownloadPaths = Utils.downloadTestStepEmbeddedAttachments(result, jiraApiKey, tsFileAttachmentDownloadLocation, testcaseKey, jiraBaseURL, "testData");
                        expectedResultAttachmentDownloadPaths = Utils.downloadTestStepEmbeddedAttachments(result, jiraApiKey, tsFileAttachmentDownloadLocation, testcaseKey, jiraBaseURL, "expectedResult");
                        descriptionAttachmentDownloadPaths.addAll(testDataAttachmentDownloadPaths);
                        descriptionAttachmentDownloadPaths.addAll(expectedResultAttachmentDownloadPaths);
                        fileAttachmentDownloadPaths = descriptionAttachmentDownloadPaths;
                    } else {
                        logger.error("Usage of jiraFileAttachmentsDownload is not correct. The argument value should be either testcase or teststep");
                        return null;
                    }
                }
            } catch (ParseException | IOException e) {
                logger.error("Error while parsing the Json response", e);
            }

        } else {
            logger.error("Response is NULL from while retrieving attachments from JIRA. Returning NULL");
        }
        return fileAttachmentDownloadPaths;
    }

    public List<JiraTestStep> getTestStepsForTestCase(String testCaseKey) {
        List<JiraTestStep> testSteps = new ArrayList<>();

        try {
            String urlString = jiraBaseURL + "/rest/atm/1.0/testcase/" + testCaseKey;
            HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + jiraApiKey);
            connection.setRequestProperty("Content-Type", "application/json");

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                JSONObject jiraTestCase = new JSONObject(response.toString());
                JSONArray stepsArray = jiraTestCase.getJSONObject("testScript").getJSONArray("steps");

                for (int i = 0; i < stepsArray.length(); i++) {
                    JSONObject stepObject = stepsArray.getJSONObject(i);

                    JiraTestStep testStep = new JiraTestStep();
                    testStep.setId(stepObject.optInt("id", 0)); // Handling ID as an integer or default to 0
                    testStep.setDescription(stepObject.optString("description", ""));
                    testStep.setExpectedResult(stepObject.optString("expectedResult", ""));
                    testStep.setIndex(stepObject.optInt("index", 0));
                    testStep.setTestData(stepObject.optString("testData", ""));

                    if (stepObject.has("attachments")) {
                        JSONArray attachmentsArray = stepObject.getJSONArray("attachments");
                        List<JiraAttachment> attachments = new ArrayList<>();
                        for (int j = 0; j < attachmentsArray.length(); j++) {
                            JSONObject attachmentObject = attachmentsArray.getJSONObject(j);
                            JiraAttachment attachment = new JiraAttachment(
                                    attachmentObject.optString("id", "0"), // Handling ID as a string
                                    attachmentObject.optString("name", ""),
                                    jiraBaseURL + "/rest/atm/1.0/attachment/" + attachmentObject.getInt("id")
                            );
                            attachments.add(attachment);
                        }
                        testStep.setAttachments(attachments);
                    }

                    testSteps.add(testStep);
                }
            } else {
                logger.error("GET request to Jira failed with response code: " + responseCode);
            }
        } catch (Exception e) {
            logger.error("Exception occurred while retrieving test steps for Jira test case: " + testCaseKey, e);
        }

        return testSteps;
    }
}
