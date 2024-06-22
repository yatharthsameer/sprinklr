package com.example.demo.model;

public class Step {
    private String description;
    private String expectToHear;
    private String replyWith;
    private String gatherTranscript;
    private String confidence; // New field

    // Constructors, getters, and setters

    public Step(String description, String expectToHear, String replyWith) {
        this.description = description;
        this.expectToHear = expectToHear;
        this.replyWith = replyWith;
        this.gatherTranscript = null;
        this.confidence = null;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getExpectToHear() {
        return expectToHear;
    }

    public void setExpectToHear(String expectToHear) {
        this.expectToHear = expectToHear;
    }

    public String getReplyWith() {
        return replyWith;
    }

    public void setReplyWith(String replyWith) {
        this.replyWith = replyWith;
    }

    public String getGatherTranscript() {
        return gatherTranscript;
    }

    public void setGatherTranscript(String gatherTranscript) {
        this.gatherTranscript = gatherTranscript;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }
}
