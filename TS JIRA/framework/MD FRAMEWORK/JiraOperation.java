package com.optum.coe.automation.rally;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.ParseException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.util.FileUtils;
import org.apache.logging.log4j.core.util.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class JiraOperation {

	private String jiraBaseURL;
	private String jiraApiKey;
	private String jiraProjectKey;
	private String max_chunk;
	private String tcFileAttachmentDownloadLocation;
	private static String tsFileAttachmentDownloadLocation;
	private static final Logger logger = LogManager.getLogger();

	public JiraOperation() {
		jiraBaseURL = ConfigLoader.getConfigValue("JIRA_BASE_URL");
		jiraApiKey = ConfigLoader.getConfigValue("JIRA_API_TOKEN");
		jiraProjectKey = ConfigLoader.getConfigValue("JIRA_PROJECT_KEY");
		max_chunk = ConfigLoader.getConfigValue("MAX_VALUE_CHUNK");
		tcFileAttachmentDownloadLocation = ConfigLoader.getConfigValue("TEST_CASE_FILE_ATTACHMENT_LOCATION");
		tsFileAttachmentDownloadLocation = ConfigLoader.getConfigValue("TEST_STEP_FILE_ATTACHMENT_LOCATION");
		logger.info("Jira values for the project key " + jiraProjectKey + " are assigned from rally_migration_config.properties file");
		logger.log(Level.getLevel("VERBOSE"),
				"Below the values assigned from rally_migration_config.properties file. \nJira Base URL - "
						+ jiraBaseURL + "\nJira Project Key " + jiraProjectKey + "\nMax Chunk value - " + max_chunk
						+ "\nTest Case File Attachment Download location - " + tcFileAttachmentDownloadLocation
						+ "\nTest Step File Attachment location - " + tsFileAttachmentDownloadLocation);
	}
	
	
	
	

	public ArrayList<String> getJiraNonMigratedTestcaseKeys() {
		ArrayList<String> testCaseKeys = new ArrayList<String>();
		String encodedKey = null;
		try {
			encodedKey = URLEncoder.encode(
					"projectKey = " + jiraProjectKey
							+ " AND \"Migrate Test to Rally\" = true AND \"TestCase Migrated\" = false",
					StandardCharsets.UTF_8.toString());
		} catch (UnsupportedEncodingException e) {
			logger.error("Error while encoding a part of URL ", e);
		}

		String url = jiraBaseURL + "/rest/atm/1.0/testcase/search?fields=key&maxResults=" + max_chunk + "&query="
				+ encodedKey;
		logger.info("String URL to get non migrated testcase keys from Jira " + url);

		HttpEntity response = Utils.getJiraResponse(url, jiraApiKey);
		if (response != null) {
			String result = null;
			try {
				result = EntityUtils.toString(response);
			} catch (ParseException | IOException e) {
				logger.error("Error while parsing the Json response ", e);
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

	public JsonObject getJiraTestCaseDetails(String key) {
		JsonObject jiraJson = null;
		String url = jiraBaseURL + "/rest/atm/1.0/testcase/" + key;
		HttpEntity response = Utils.getJiraResponse(url, jiraApiKey);
		if (response != null) {
			try {
				String responseBody = EntityUtils.toString(response);
				logger.info("Testcase details for the key" + key + ": " + responseBody);
				jiraJson = JsonParser.parseString(responseBody).getAsJsonObject();
			} catch (ParseException | IOException e) {
				logger.error("Failed to retrieve JIRA testcase details for the key " + key, e);
			}
		} else {
			logger.error("Failed to get jira testcase details for the key " + key + "; Returning null");
		}
		return jiraJson;
	}
	
	
	
	//  method to download embedded images from JIRA test steps
    public static List<String> downloadStepAttachments(JiraTestStep step) {
        List<String> attachmentPaths = new ArrayList<>();
        
        for (JiraAttachment attachment : step.getAttachments()) {
            try {
                String downloadUrl = attachment.getUrl();
                String localPath = tsFileAttachmentDownloadLocation + "/" + attachment.getFileName();
                
                downloadFileFromURL(downloadUrl, localPath);
                
                attachmentPaths.add(localPath);
            } catch (IOException e) {
                logger.error("Failed to download attachment: " + attachment.getFileName(), e);
            }
        }
        
        return attachmentPaths;
    }
	
	
	
	
    // Alternative method to download a file from a URL without using Apache Commons IO
    private static void downloadFileFromURL(String fileUrl, String destination) throws IOException {
        try (InputStream in = new BufferedInputStream(new URL(fileUrl).openStream());
             FileOutputStream out = new FileOutputStream(destination)) {
            byte[] dataBuffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                out.write(dataBuffer, 0, bytesRead);
            }
        }
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
			logger.info("URL String for teststep attachments: " + testAttachmentUrl);
		} else {
			logger.error("Usage of jiraFileAttachmentsDownload is not correct. The argument value should be either testcase or teststep");
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
					logger.info("Attachment URL is found for the testcase key " + testcaseKey + "; JSON body: " + result);
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
						logger.error("Usage of jiraFileAttachmentsDownload is not correct. The argument value should be either testcase or teststep");
						return null;
					}
				}
			} catch (ParseException | IOException e) {
				logger.error("Error while parsing the Json response ", e);
			}
		} else {
			logger.error("Response is NULL while retrieving non migrated keys from JIRA. Returning NULL");
		}
		return fileAttachmentDownloadPaths;
	}
	  public List<String> downloadEmbeddedImages(JiraTestStep step) {
	        List<String> embeddedImagePaths = new ArrayList<>();

	        // Assume that JiraTestStep has a method to get embedded image URLs
	        List<String> imageUrls = step.getEmbeddedImageUrls(); // You need to implement getEmbeddedImageUrls()

	        for (String imageUrl : imageUrls) {
	            try {
	                String imagePath = downloadImage(imageUrl);
	                embeddedImagePaths.add(imagePath);
	            } catch (IOException e) {
	                logger.error("Failed to download embedded image from URL: " + imageUrl, e);
	            }
	        }

	        return embeddedImagePaths;
	    }
	  private String downloadImage(String imageUrl) throws IOException {
	        URL url = new URL(imageUrl);
	        String fileName = "downloaded_" + System.currentTimeMillis() + "_" + url.getFile().substring(url.getFile().lastIndexOf('/') + 1);
	        File file = new File("downloads/" + fileName);
	        file.getParentFile().mkdirs();

	        try (InputStream in = new BufferedInputStream(url.openStream());
	             FileOutputStream fos = new FileOutputStream(file)) {

	            byte[] buffer = new byte[1024];
	            int bytesRead;
	            while ((bytesRead = in.read(buffer)) != -1) {
	                fos.write(buffer, 0, bytesRead);
	            }
	        }

	        return file.getAbsolutePath();
	    }
	
	
	// Method to fetch test steps from JIRA for a given test case ID
	public List<JiraTestStep> getTestSteps(String testCaseId) {
		List<JiraTestStep> testSteps = new ArrayList<>();
		CloseableHttpClient httpClient = HttpClients.createDefault();

		try {
			String url = jiraBaseURL + "/rest/api/2/issue/" + testCaseId + "/teststeps";
			HttpGet request = new HttpGet(url);
			request.addHeader("Authorization", "Basic " + jiraApiKey);

			HttpResponse response = (HttpResponse) httpClient.execute(request);
			String jsonResponse = EntityUtils.toString(((org.apache.http.HttpResponse) response).getEntity());

			JSONObject jsonObject = new JSONObject(jsonResponse);
			JSONArray stepsArray = jsonObject.getJSONArray("testSteps");
			
			   logger.info("URL used to fetch test steps: " + url);
               logger.info("Number of teststeps retrieved: " + stepsArray.length());
			
			
			

			for (int i = 0; i < stepsArray.length(); i++) {
				JSONObject stepJson = stepsArray.getJSONObject(i);
				JiraTestStep step = new JiraTestStep();
				step.setId(stepJson.getInt("id"));
				step.setDescription(stepJson.getString("description"));
				step.setExpectedresult(stepJson.getString("expectedResult"));
				step.setIndex(stepJson.getInt("index"));
				step.setTestdata(stepJson.getString("testData"));
				testSteps.add(step);
			}

			logger.info("Fetched " + testSteps.size() + " test steps for test case ID: " + testCaseId);

		} catch (Exception e) {
			logger.error("Error fetching test steps for test case ID: " + testCaseId, e);
		} finally {
			try {
				httpClient.close();
			} catch (Exception e) {
				logger.error("Error closing HttpClient", e);
			}
		}

		return testSteps;
	}
	
}
