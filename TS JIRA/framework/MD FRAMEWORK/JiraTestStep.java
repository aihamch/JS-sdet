package com.optum.coe.automation.rally;

import java.util.List;

public class JiraTestStep {

    private int id; // Changed to int to match JSON data type
    private String description;
    private String expectedResult;
    private String testData;
    private int index; // Changed to int to match JSON data type
    private List<JiraAttachment> attachments;
    private List<String> embeddedImageUrls;

    // Getters and Setters for all fields
    public int getId() {
        return id;
    }

    public void setId(int id) { // Updated to handle int
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getExpectedResult() {
        return expectedResult;
    }

    public void setExpectedResult(String expectedResult) {
        this.expectedResult = expectedResult;
    }

    public String getTestData() {
        return testData;
    }

    public void setTestData(String testData) {
        this.testData = testData;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) { // Updated to handle int
        this.index = index;
    }

    public List<JiraAttachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<JiraAttachment> attachments) {
        this.attachments = attachments;
    }

    public List<String> getEmbeddedImageUrls() {
        return embeddedImageUrls;
    }

    public void setEmbeddedImageUrls(List<String> embeddedImageUrls) {
        this.embeddedImageUrls = embeddedImageUrls;
    }
}
