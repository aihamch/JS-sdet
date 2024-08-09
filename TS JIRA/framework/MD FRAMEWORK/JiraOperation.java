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

    /*
     * A Constructor loads the value from .properties file. These value will be
     * loaded as soon as a object is created for this class. Jira Base URL, Jira API
     * Key, Jira Project Key, Max Chunk values are loaded from .properties file
     * max_chunk value refers that how many test cases should be migrated for a run.
     */
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

    /*
     * A method to get non migrated testcase keys using JIRA REST Api and
     * save them to Testcase array list
     */
    public ArrayList<String> getJiraNonMigratedTestcaseKeys() {
        // An ArrayList is used to store the testcases keys from Jira response
        ArrayList<String> testCaseKeys = new ArrayList<String>();

        // Preparation of URL string building.
        String encodedKey = null;
        try {
            encodedKey = URLEncoder.encode(
                    "projectKey = " + jiraProjectKey
                            + " AND \"Migrate Test to Rally\" = true AND \"TestCase Migrated\" = false",
                    StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            logger.error("Error while encoding a part of URL ", e);
        }

        /*
         * Jira URL string building. This URL will give the Jira Testcase keys
         * 
         * 1. For max chunk configured numbers testcases. "max_result" is the URL
         * argument which handles this part. AND 2. For the testcases which are not
         * migrated already. It will be determined by "TestCase Migrated" field which
         * will be false for migrated testcase in Jira AND 3. For the testcases which
         * need to be migrated. It will be determined by "Migrate Test to Rally" field
         * which will be true for non-migrated testcase in Jira AND 4. For the testcases
         * associated with the configured Jira project key
         */
        String url = jiraBaseURL + "/rest/atm/1.0/testcase/search?fields=key&maxResults=" + maxChunk + "&query=" + encodedKey;
        logger.info("String URL to get non migrated testcase keys from Jira " + url);

        /*
         * Call "getJiraResponse" from Utils class. This method will return the JIRA
         * JSON response for the given URL. Since the URL has been built such a way that
         * to list the non migrated testcases, the output of this method would be Jira
         * testcase keys in a JSON format
         */
        HttpEntity response = Utils.getJiraResponse(url, jiraApiKey);

        if (response != null) {
            String result = null;
            try {
                // Parse the Json into string; meaning get the jira testcase keys as string
                // value from Json
                result = EntityUtils.toString(response);
            } catch (ParseException | IOException e) {
                logger.error("Error while parsing the Json response", e);
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

    /*
     * Create a method to get the testcase details for a given jira testcase key.
     * This method accepts Jira key as string argument and returns the testcase keys
     * as Json Object for further processing Create a method to get JIRA Test case
     * details for the current key
     */
    public JSONObject getJiraTestCaseDetails(String testCaseKey) {
        String jsonResponse = getJiraResponse(testCaseKey);
        JSONObject jiraTestcaseJson = new JSONObject(jsonResponse);
        return jiraTestcaseJson;
    }

    // Helper method to perform HTTP GET request to Jira and return the response as a string
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

    /*
     * Create method to download the attachment from Jira Test case For a given test
     * case key, this method downloads Test case level File Attachments, Not
     * Embedded Attachments Testcase key is given as a String Argument Type is given
     * as another String Argument. The "type" should be either
     * "testcase" or "teststep"
     */
    public List<String> jiraAttachmentsDownload(String testcaseKey, String testType, String attachmentType) {
        // A String is used to return the file attachments download paths
        List<String> fileAttachmentDownloadPaths = null;
        String testAttachmentUrl = null;
        if (testType.equals("testcase")) {
            testAttachmentUrl = jiraBaseURL + "/rest/atm/1.0/testcase/" + testcaseKey + "/attachments";
            logger.info("URL String for testcase attachments: " + testAttachmentUrl);
        } else if (testType.equals("teststep") && attachmentType.equals("file")) {
            logger.info("URL String for teststep attachments: " + testAttachmentUrl);
            testAttachmentUrl = jiraBaseURL + "/rest/atm/1.0/testcase/" + testcaseKey;
        } else if (testType.equals("teststep") && attachmentType.equals("embedded")) {
            logger.info("URL String for teststep attachments: " + testAttachmentUrl);
            testAttachmentUrl = jiraBaseURL + "/rest/atm/1.0/testcase/" + testcaseKey;
        } else {
            logger.error(
                    "Usage of jiraFileAttachmentsDownload is not correct. The argument value should be either testcase or teststep");
            return null;
        }
        HttpEntity response = Utils.getJiraResponse(testAttachmentUrl, jiraApiKey);
        if (response != null) {
            String result = null;
            try {
                /*
                 * Parse the Json into string; meaning get the jira testcase keys as string
                 * value from Json. No Attachment is found when JSON response is empty
                 * Attachment is found when JSON response is not empty
                 */
                result = EntityUtils.toString(response);
                if (result.trim().isEmpty() || result.equals("{}") || result.equals("[]")) {
                    logger.info("No Attachment URL found for the testcase key " + testcaseKey);
                } else {
                    logger.info("Attachment URL is found for the testcase key " + testcaseKey
                            + "; JSON body while finding the attachment URL and name of the attachment: " + result);

                    if (testType.equals("testcase")) {
                        Map<String, String> testMap = Utils.pharseJsonGetAttachmentUrlAndName(result);
                        fileAttachmentDownloadPaths = Utils.downloadFileAttachmentFromJiraTestCase(testMap,
                                tcFileAttachmentDownloadLocation, jiraApiKey, testcaseKey);
                    } else if (testType.equals("teststep") && attachmentType.equals("file")) {
                        fileAttachmentDownloadPaths = Utils.downloadFileAttachmentFromTestStep(result, jiraApiKey,
                                tsFileAttachmentDownloadLocation, testcaseKey, jiraBaseURL);
                    } else if (testType.equals("teststep") && attachmentType.equals("embedded")) {

                        List<String> descriptionAttachmentDownloadPaths, testDataAttachmentDownloadPaths, expectedResultAttachmentDownloadPaths;
                        descriptionAttachmentDownloadPaths = Utils.downloadTestStepEmbeddedAttachments(result, jiraApiKey,
                                tsFileAttachmentDownloadLocation, testcaseKey, jiraBaseURL, "description");
                        testDataAttachmentDownloadPaths = Utils.downloadTestStepEmbeddedAttachments(result, jiraApiKey,
                                tsFileAttachmentDownloadLocation, testcaseKey, jiraBaseURL, "testData");
                        expectedResultAttachmentDownloadPaths = Utils.downloadTestStepEmbeddedAttachments(result, jiraApiKey,
                                tsFileAttachmentDownloadLocation, testcaseKey, jiraBaseURL, "expectedResult");
                        descriptionAttachmentDownloadPaths.addAll(testDataAttachmentDownloadPaths);
                        descriptionAttachmentDownloadPaths.addAll(expectedResultAttachmentDownloadPaths);
                        fileAttachmentDownloadPaths = descriptionAttachmentDownloadPaths;

                    } else {
                        logger.error(
                                "Usage of jiraFileAttachmentsDownload is not correct. The argument value should be either testcase or teststep");
                        return null;
                    }
                }
            } catch (ParseException | IOException e) {
                logger.error("Error while parsing the Json response", e);
            }

        } else {
            // Log if null response
            logger.error("Response is NULL from while retrieving non migrated keys from JIRA. Returning NULL");
        }
        return fileAttachmentDownloadPaths;
    }

    // Method to download attachments for a given Jira test step
    public static List<String> downloadStepAttachments(JiraTestStep step) {
        List<String> attachmentPaths = new ArrayList<>();

        // Download file attachments from the test step
        List<JiraAttachment> attachments = step.getAttachments();
        if (attachments != null && !attachments.isEmpty()) {
            for (JiraAttachment attachment : attachments) {
                try {
                    String fileUrl = attachment.getUrl();
                    String fileName = attachment.getFilename();
                    String downloadLocation = ConfigLoader.getConfigValue("TEST_STEP_FILE_ATTACHMENT_LOCATION");

                    HttpEntity response = Utils.getJiraResponse(fileUrl, ConfigLoader.getConfigValue("JIRA_API_TOKEN"));

                    if (response != null) {
                        Path path = Paths.get(downloadLocation);
                        if (!Files.exists(path)) {
                            Files.createDirectories(path);
                        }

                        Path filePath = Paths.get(downloadLocation + "/" + fileName);
                        try (InputStream in = response.getContent()) {
                            Files.copy(in, filePath);
                            attachmentPaths.add(filePath.toString());
                            EntityUtils.consume(response);
                            logger.info("Downloaded test step attachment: " + fileName + " to " + filePath);
                        } catch (IOException e) {
                            logger.error("Failed to download test step attachment: " + fileName, e);
                        }
                    } else {
                        logger.error("No response received when trying to download attachment: " + fileName);
                    }
                } catch (IOException e) {
                    logger.error("Error while downloading attachment for test step", e);
                }
            }
        }

        // Download embedded images from the test step (if any)
        List<String> embeddedImageUrls = step.getEmbeddedImageUrls();
        if (embeddedImageUrls != null && !embeddedImageUrls.isEmpty()) {
            String embeddedDownloadLocation = ConfigLoader.getConfigValue("TEST_STEP_EMBEDDED_ATTACHMENT_LOCATION");
            for (String imageUrl : embeddedImageUrls) {
                try {
                    HttpEntity response = Utils.getJiraResponse(imageUrl, ConfigLoader.getConfigValue("JIRA_API_TOKEN"));

                    if (response != null) {
                        Path path = Paths.get(embeddedDownloadLocation);
                        if (!Files.exists(path)) {
                            Files.createDirectories(path);
                        }

                        String[] parts = imageUrl.split("/");
                        String imageName = parts[parts.length - 1];
                        Path filePath = Paths.get(embeddedDownloadLocation + "/" + imageName);

                        try (InputStream in = response.getContent()) {
                            Files.copy(in, filePath);
                            attachmentPaths.add(filePath.toString());
                            EntityUtils.consume(response);
                            logger.info("Downloaded embedded image: " + imageName + " to " + filePath);
                        } catch (IOException e) {
                            logger.error("Failed to download embedded image: " + imageName, e);
                        }
                    } else {
                        logger.error("No response received when trying to download embedded image: " + imageName);
                    }
                } catch (IOException e) {
                    logger.error("Error while downloading embedded image for test step", e);
                }
            }
        }

        return attachmentPaths;
    }

    // Method to retrieve test steps for a given Jira test case key
    public List<JiraTestStep> getTestStepsForTestCase(String testCaseKey) {
        List<JiraTestStep> testSteps = new ArrayList<>();

        try {
            // Construct the URL to retrieve the test case details, including test steps
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
                    testStep.setId(stepObject.getInt("id")); // Handling ID as an integer
                    testStep.setDescription(stepObject.getString("description"));
                    testStep.setExpectedResult(stepObject.getString("expectedResult"));
                    testStep.setIndex(stepObject.getInt("index"));
                    testStep.setTestData(stepObject.optString("testData", ""));

                    // Retrieve attachments if available
                    if (stepObject.has("attachments")) {
                        JSONArray attachmentsArray = stepObject.getJSONArray("attachments");
                        List<JiraAttachment> attachments = new ArrayList<>();
                        for (int j = 0; j < attachmentsArray.length(); j++) {
                            JSONObject attachmentObject = attachmentsArray.getJSONObject(j);
                            JiraAttachment attachment = new JiraAttachment(
                                    attachmentObject.getString("id"),
                                    attachmentObject.getString("name"),
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
