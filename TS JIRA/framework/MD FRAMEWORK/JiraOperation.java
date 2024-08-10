I have found the missing methods in your provided `JiraOperation.java` file. I will now integrate these methods into the updated version I provided earlier, maintaining the structure and comments, and ensuring the use of `ConfigLoader` to fetch configuration data.

### Full `JiraOperation.java` with Missing Methods Integrated:

```java
package com.optum.coe.automation.rally;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JiraOperation {

    private static final Logger logger = LogManager.getLogger();

    // Configuration values loaded from rally_migration_config.properties
    private final String jiraBaseURL;
    private final String jiraApiKey;
    private final String jiraProjectKey;
    private final String maxChunk;
    private final String tcFileAttachmentDownloadLocation;
    private final String tsFileAttachmentDownloadLocation;

    /**
     * Constructor that initializes JiraOperation with configuration values.
     */
    public JiraOperation() {
        this.jiraBaseURL = ConfigLoader.getConfigValue("JIRA_BASE_URL");
        this.jiraApiKey = ConfigLoader.getConfigValue("JIRA_API_TOKEN");
        this.jiraProjectKey = ConfigLoader.getConfigValue("JIRA_PROJECT_KEY");
        this.maxChunk = ConfigLoader.getConfigValue("MAX_VALUE_CHUNK");
        this.tcFileAttachmentDownloadLocation = ConfigLoader.getConfigValue("TEST_CASE_FILE_ATTACHMENT_LOCATION");
        this.tsFileAttachmentDownloadLocation = ConfigLoader.getConfigValue("TEST_STEP_FILE_ATTACHMENT_LOCATION");

        logger.info("Jira values for the project key " + jiraProjectKey + " are assigned from rally_migration_config.properties file");
        logger.log(Level.getLevel("VERBOSE"),
                "Below the values assigned from rally_migration_config.properties file. \nJira Base URL - " + jiraBaseURL
                        + "\nJira Project Key " + jiraProjectKey + "\nMax Chunk value - " + maxChunk
                        + "\nTest Case File Attachment Download location - " + tcFileAttachmentDownloadLocation
                        + "\nTest Step File Attachment location - " + tsFileAttachmentDownloadLocation);
    }

    /**
     * Method to get non-migrated test case keys from Jira.
     *
     * @return ArrayList of non-migrated test case keys.
     */
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
                logger.info("Key retrieved: " + jsonObject.getString("key"));
                testCaseKeys.add(key);
            }

        } else {
            logger.error("Response is NULL from while retrieving non migrated keys from JIRA. Returning NULL");
        }

        return testCaseKeys;
    }

    /**
     * Method to get Jira test case details based on the provided test case key.
     *
     * @param testCaseKey The key of the test case.
     * @return The test case details as a JsonObject.
     */
    public JsonObject getJiraTestCaseDetails(String testCaseKey) {
        String jsonResponse = getJiraResponse(testCaseKey);

        JSONObject jiraTestcaseJson = new JSONObject(jsonResponse);

        JsonObject gsonTestcaseJson = JsonParser.parseString(jiraTestcaseJson.toString()).getAsJsonObject();

        return gsonTestcaseJson;
    }

    /**
     * Helper method to perform HTTP GET request to Jira and return the response as a string.
     *
     * @param testCaseKey The key of the test case.
     * @return The response as a string.
     */
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

    /**
     * Method to download attachments from Jira test case.
     *
     * @param testcaseKey The key of the test case.
     * @param testType    The type of the test ("testcase" or "teststep").
     * @param attachmentType The type of the attachment ("file" or "embedded").
     * @return List of file paths where the attachments are downloaded.
     */
    public List<String> jiraAttachmentsDownload(String testcaseKey, String testType, String attachmentType) {
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
            logger.error("Response is NULL from while retrieving non migrated keys from JIRA. Returning NULL");
        }
        return fileAttachmentDownloadPaths;
    }

    /**
     * Method to download attachments for a given Jira test step.
     *
     * @param step The JiraTestStep object representing the step.
     * @return List of file paths where the attachments are downloaded.
     */
    public static List<String> downloadStepAttachments(JiraTestStep step) {
        List<String> attachmentPaths = new ArrayList<>();

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
                        logger.error("No response received when trying to download embedded image: ");
                    }
                } catch (IOException e) {
                    logger.error("Error while downloading embedded image for test step", e);
                }
            }
        }

        return attachmentPaths;
    }

    /**
     * Method to retrieve test steps for a given Jira test case key.
     *
     * @param testCaseKey The key of the test case.
     * @return List of JiraTestStep objects representing the test steps.
     */
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
                    testStep.setId(stepObject.getInt("id"));
                    testStep.setDescription(stepObject.getString("description"));
                    testStep.setExpectedResult(stepObject.getString("expectedResult"));
                    testStep.setIndex(stepObject.getInt("index"));
                    testStep.setTestData(stepObject.optString("testData", ""));

                    if (stepObject.has("attachments")) {
                        JSONArray attachmentsArray = stepObject.getJSONArray("attachments");
                        List<JiraAttachment> attachments = new ArrayList<>();
                        for (int j = 0; j < attachmentsArray.length(); j++) {
                            JSONObject attachmentObject = attachmentsArray.getJSONObject(j);
                            JiraAttachment attachment = new JiraAttachment(
                                    attachmentObject                            JiraAttachment attachment = new JiraAttachment(
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
