package com.example.demo.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Document
public class Campaign {
    @Id
    private String id;
    private String campaignName;
    private String twimlInstruction;
    private int numberOfCalls;
    private List<CallDetail> callDetails = new ArrayList<>();
    private String expectedTranscription;
    private String twimlFilePath; // New field
    private int lastHandledGatherIndex = -1; // New field to keep track of the last handled gather index

    // Constructors, getters, and setters

    public Campaign(String campaignName, String twimlInstruction, int numberOfCalls, String expectedTranscription, String twimlFilePath) {
        this.campaignName = campaignName;
        this.twimlInstruction = twimlInstruction;
        this.numberOfCalls = numberOfCalls;
        this.expectedTranscription = expectedTranscription;
        this.twimlFilePath = twimlFilePath;
    }

    public String getCampaignName() {
        return campaignName;
    }

    public String getTwimlInstruction() {
        return twimlInstruction;
    }

    public void setTwimlInstruction(String twimlInstruction) {
        this.twimlInstruction = twimlInstruction;
    }

    public int getNumberOfCalls() {
        return numberOfCalls;
    }

    public List<CallDetail> getCallDetails() {
        return callDetails;
    }

    public void addCallDetail(CallDetail callDetail) {
        this.callDetails.add(callDetail);
    }

    public String getExpectedTranscription() {
        return expectedTranscription;
    }

    public void setExpectedTranscription(String expectedTranscription) {
        this.expectedTranscription = expectedTranscription;
    }

    public String getTwimlFilePath() {
        return twimlFilePath;
    }

    public void setTwimlFilePath(String twimlFilePath) {
        this.twimlFilePath = twimlFilePath;
    }

    public int getLastHandledGatherIndex() {
        return lastHandledGatherIndex;
    }

    public void setLastHandledGatherIndex(int lastHandledGatherIndex) {
        this.lastHandledGatherIndex = lastHandledGatherIndex;
    }
}
