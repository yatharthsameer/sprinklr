package com.example.demo.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Document
public class CallDetail {
    @Id
    private String id;
    private String sessionId;
    private String callSid;
    private String transcript;
    private String base64EncodedAudio;
    private String audioFilePath;
    private List<Step> steps = new ArrayList<>();
    private int lastHandledGatherIndex;

    // Constructors, getters, and setters


    public CallDetail(String sessionId, String callSid) {
        this.sessionId = sessionId;
        this.callSid = callSid;
        this.transcript = "";
        this.base64EncodedAudio = "";
        this.audioFilePath = "";
        this.lastHandledGatherIndex = -1;  // Default value
    }

    public void initializeLastHandledGatherIndex(String twimlInstruction) {
        String[] lines = twimlInstruction.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("</Gather>")) {
                this.lastHandledGatherIndex = i;
                break;
            }
        }
    }


    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getCallSid() {
        return callSid;
    }

    public void setCallSid(String callSid) {
        this.callSid = callSid;
    }

    public String getTranscript() {
        return transcript;
    }

    public void addTranscript(String transcript) {
        this.transcript += transcript;
    }

    public String getBase64EncodedAudio() {
        return base64EncodedAudio;
    }

    public void addBase64EncodedAudio(String base64EncodedAudio) {
        this.base64EncodedAudio += base64EncodedAudio;
    }

    public String getAudioFilePath() {
        return audioFilePath;
    }

    public void setAudioFilePath(String audioFilePath) {
        this.audioFilePath = audioFilePath;
    }

    public List<Step> getSteps() {
        return steps;
    }

    public void addStep(Step step) {
        this.steps.add(step);
    }

    public int getLastHandledGatherIndex() {
        return lastHandledGatherIndex;
    }

    public void setLastHandledGatherIndex(int lastHandledGatherIndex) {
        this.lastHandledGatherIndex = lastHandledGatherIndex;
    }
}
