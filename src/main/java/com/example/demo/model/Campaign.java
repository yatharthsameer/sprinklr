package com.example.demo.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Document
public class Campaign {
    @Id
    private String id;
    private String campaignName;
    private String twimlInstruction;
    private int numberOfCalls;
    private List<CallDetail> callDetails = new ArrayList<>();
    private String twimlFilePath;
    private String randomIndex;

    // No-argument constructor
    public Campaign() {
        this.randomIndex = UUID.randomUUID().toString();
    }

    public Campaign(String campaignName, String twimlInstruction, int numberOfCalls, String twimlFilePath) {
        this.campaignName = campaignName;
        this.twimlInstruction = twimlInstruction;
        this.numberOfCalls = numberOfCalls;
        this.twimlFilePath = twimlFilePath;
        this.randomIndex = UUID.randomUUID().toString(); // Generate a random unique index
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

    public String getTwimlFilePath() {
        return twimlFilePath;
    }

    public void setTwimlFilePath(String twimlFilePath) {
        this.twimlFilePath = twimlFilePath;
    }



    public String getRandomIndex() {
        return randomIndex;
    }

    public void setRandomIndex(String randomIndex) {
        this.randomIndex = randomIndex;
    }
}
