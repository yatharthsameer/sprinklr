package com.example.demo.controller;

import com.example.demo.model.CallDetail;
import com.example.demo.model.Campaign;
import com.example.demo.model.Step;
import com.example.demo.repository.CampaignRepository;
import com.example.demo.service.AudioService;
import com.example.demo.service.TwiMLHandler;
import com.twilio.Twilio;
import com.twilio.http.HttpMethod;
import com.twilio.rest.api.v2010.account.Call;
import com.twilio.type.PhoneNumber;
import com.twilio.type.Twiml;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

@RestController
public class CallController {

    
    private AtomicInteger completedCalls;
    private DeferredResult<ResponseEntity<Object>> deferredResult;
    private int totalCalls;
    private static final Logger logger = Logger.getLogger(CallController.class.getName());
    @Getter
    private final Map<String, Campaign> campaigns = new HashMap<>();
    @Getter
    private final Map<String, String> sessionToCallMap = new HashMap<>();
    @Getter
    private final Map<String, String> callSidToCampaignMap = new HashMap<>();

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private AudioService audioService;

    @Autowired
    private TwiMLHandler twiMLHandler;

    public CallController() {
        Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
    }

    @PostMapping("/createCalls")
    public DeferredResult<ResponseEntity<Object>> createCalls(
            @RequestBody Map<String, Object> payload) throws IOException {

        String campaignName = (String) payload.get("campaignName");
        String to = (String) payload.get("to");
        String from = (String) payload.get("from");
        int numberOfConcurrentCalls = (int) payload.get("numberOfConcurrentCalls");

        List<Map<String, Object>> steps = (List<Map<String, Object>>) payload.get("steps");

        StringBuilder twimlBuilder = new StringBuilder();
        twimlBuilder.append("<Response>\n");
        twimlBuilder.append("<Start>\n");
        twimlBuilder.append("<Stream track=\"inbound_track\" url=\"wss://b4fa-14-194-6-234.ngrok-free.app/ws\">\n");
        twimlBuilder.append("</Stream>\n");
        twimlBuilder.append("</Start>\n");

        for (Map<String, Object> step : steps) {
            String replyWith = (String) step.get("replyWith");
            twimlBuilder.append(replyWith).append("\n");
        }

        twimlBuilder.append("</Response>");

        String twiml = twimlBuilder.toString();
        String filePath = "twiml_" + campaignName + ".txt";
        twiMLHandler.saveTwiMLToFile(twiml, filePath);

        Campaign campaign = new Campaign(campaignName, twiml, numberOfConcurrentCalls, filePath);
        campaigns.put(campaignName, campaign);
        campaignRepository.save(campaign); // Save initial campaign state

        for (int i = 0; i < numberOfConcurrentCalls; i++) {
            Call call = Call.creator(new PhoneNumber(to), new PhoneNumber(from), new Twiml(twiml))
                    .setMethod(HttpMethod.GET)
                    .setRecord(true)
                    .setStatusCallback("https://b4fa-14-194-6-234.ngrok-free.app/events")
                    .setStatusCallbackMethod(HttpMethod.POST)
                    .setStatusCallbackEvent(Arrays.asList("completed"))
                    .create();
            logger.info("Call initiated with SID: " + call.getSid());
            CallDetail callDetail = new CallDetail(null, call.getSid()); // Initialize with null sessionId
            callDetail.initializeLastHandledGatherIndex(twiml); // Initialize the lastHandledGatherIndex

            // Store steps in CallDetail
            for (Map<String, Object> step : steps) {
                String description = (String) step.get("description");
                String expectToHear = (String) step.get("expectToHear");
                String replyWith = (String) step.get("replyWith");
                Step callStep = new Step(description, expectToHear, replyWith);
                callDetail.addStep(callStep);
            }

            campaign.addCallDetail(callDetail);
            callSidToCampaignMap.put(call.getSid(), campaignName); // Map callSid to campaignName
        }

        deferredResult = new DeferredResult<>();
        totalCalls = numberOfConcurrentCalls;
        completedCalls = new AtomicInteger(0);

        return deferredResult;
    }

    @PostMapping("/events")
    public void handleStatusCallback(
            @RequestParam String CallSid,
            @RequestParam String CallStatus) {
        logger.info("Received status callback for call SID: " + CallSid + " with status: " + CallStatus);
        if ("completed".equals(CallStatus)) {
            String sessionId = getSessionIdByCallSid(CallSid);
            if (sessionId != null) {
                try {
                    String audioFilePath = audioService.startRecording(sessionId);
                    updateAudioFilePath(CallSid, audioFilePath);
                } catch (IOException e) {
                    logger.severe("Failed to start recording: " + e.getMessage());
                }
            }

            if (completedCalls.incrementAndGet() == totalCalls) {
                Map<String, String> response = new HashMap<>();
                response.put("message", "All calls have completed");
                deferredResult.setResult(ResponseEntity.ok(response));
                printCampaignDetails(); // Print campaign details after all calls have completed
            }
        } else {
            logger.info("Received non-completed status: " + CallStatus + " for call SID: " + CallSid);
        }
    }

    @GetMapping("/campaigns")
    public ResponseEntity<List<Campaign>> getAllCampaigns() {
        List<Campaign> campaigns = campaignRepository.findAll();
        return ResponseEntity.ok(campaigns);
    }

    @GetMapping("/campaign/{randomIndex}")
    public ResponseEntity<Campaign> getCampaignByRandomIndex(@PathVariable String randomIndex) {
        Campaign campaign = campaignRepository.findByRandomIndex(randomIndex);
        if (campaign == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(campaign);
    }

    @GetMapping("/audio/{sessionId}")
    public ResponseEntity<byte[]> getAudio(@PathVariable String sessionId) {
        logger.info("Received request for audio with sessionId: " + sessionId);

        Campaign campaign = campaignRepository.findByCallDetailsSessionId(sessionId);
        if (campaign == null) {
            logger.severe("No campaign found for sessionId: " + sessionId);
            return ResponseEntity.notFound().build();
        }

        CallDetail callDetail = campaign.getCallDetails().stream()
                .filter(cd -> sessionId.equals(cd.getSessionId()))
                .findFirst()
                .orElse(null);

        if (callDetail == null) {
            logger.severe("No CallDetail found for sessionId: " + sessionId);
            return ResponseEntity.notFound().build();
        }

        String filePath = callDetail.getAudioFilePath();
        logger.info("Resolved filePath: " + filePath);

        if (filePath != null) {
            try {
                File file = new File(filePath);
                logger.info("Attempting to read file: " + file.getAbsolutePath());

                if (!file.exists()) {
                    logger.severe("File does not exist: " + file.getAbsolutePath());
                    return ResponseEntity.notFound().build();
                }

                FileInputStream fileInputStream = new FileInputStream(file);
                byte[] audioBytes = fileInputStream.readAllBytes();
                fileInputStream.close();
                logger.info("Successfully read file: " + file.getAbsolutePath());
                return ResponseEntity.ok(audioBytes);
            } catch (IOException e) {
                logger.severe("Failed to read audio file: " + e.getMessage());
                return ResponseEntity.status(500).build();
            }
        } else {
            logger.severe("File path is null for sessionId: " + sessionId);
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/gather")
    public ResponseEntity<String> handleGatherCallback(
            @RequestParam("CallSid") String callSid,
            @RequestParam("SpeechResult") String speechResult,
            @RequestParam("Confidence") String confidence) throws IOException {
        logger.info("Gather callback received");
        logger.info("Transcript: " + speechResult);
        logger.info("Confidence: " + confidence);

        // Get the campaign associated with this callSid
        String campaignName = callSidToCampaignMap.get(callSid);
        Campaign campaign = campaigns.get(campaignName);

        // Update the transcript for the current step
        CallDetail callDetail = getCallDetailByCallSid(callSid, campaign);
        if (callDetail != null) {
            for (Step step : callDetail.getSteps()) {
                if (step.getReplyWith().contains("<Gather") && step.getGatherTranscript() == null) {
                    step.setGatherTranscript(speechResult);
                    step.setConfidence(confidence);
                    break;
                }
            }
        }

        // Read TwiML instructions from file
        List<String> twimlLines = twiMLHandler.readTwiMLFromFile(campaign.getTwimlFilePath());

        // Determine the next set of instructions to return
        int lastHandledGatherIndex = callDetail.getLastHandledGatherIndex();
        StringBuilder responseTwiml = new StringBuilder();
        responseTwiml.append("<Response>\n");

        for (int i = lastHandledGatherIndex + 1; i < twimlLines.size(); i++) {
            String line = twimlLines.get(i);
            responseTwiml.append(line).append("\n");
            if (line.contains("</Gather>")) {
                callDetail.setLastHandledGatherIndex(i); // Update the last handled gather index
                break;
            }
        }

        responseTwiml.append("</Response>");

        // Save the call detail and campaign state
        campaignRepository.save(campaign);
        logger.info("responseTwiml: " + responseTwiml);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(responseTwiml.toString());
    }

    private CallDetail getCallDetailByCallSid(String callSid, Campaign campaign) {
        for (CallDetail callDetail : campaign.getCallDetails()) {
            if (callDetail.getCallSid().equals(callSid)) {
                return callDetail;
            }
        }
        return null;
    }

    private String getSessionIdByCallSid(String callSid) {
        for (Map.Entry<String, String> entry : sessionToCallMap.entrySet()) {
            if (entry.getValue().equals(callSid)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public void updateSessionId(String callSid, String sessionId) {
        sessionToCallMap.put(sessionId, callSid);
        logger.info("Updated sessionToCallMap: sessionId -> " + sessionId + ", callSid -> " + callSid);

        String campaignName = callSidToCampaignMap.get(callSid);
        if (campaignName != null) {
            Campaign campaign = campaigns.get(campaignName);
            for (CallDetail callDetail : campaign.getCallDetails()) {
                if (callDetail.getCallSid().equals(callSid)) {
                    callDetail.setSessionId(sessionId);
                    logger.info("Updated CallDetail with sessionId: " + sessionId);
                    break;
                }
            }
            campaignRepository.save(campaign); // Update campaign in MongoDB
            logger.info("Saved updated campaign: " + campaignName);
        } else {
            logger.severe("No campaign found for callSid: " + callSid);
        }
    }

    private void updateAudioFilePath(String callSid, String audioFilePath) {
        String campaignName = callSidToCampaignMap.get(callSid);
        if (campaignName != null) {
            Campaign campaign = campaigns.get(campaignName);
            for (CallDetail callDetail : campaign.getCallDetails()) {
                if (callDetail.getCallSid().equals(callSid)) {
                    callDetail.setAudioFilePath(audioFilePath);
                    logger.info("Updated CallDetail with audioFilePath: " + audioFilePath);
                    break;
                }
            }
            campaignRepository.save(campaign); // Update campaign in MongoDB
            logger.info("Saved updated campaign: " + campaignName);
        } else {
            logger.severe("No campaign found for callSid: " + callSid);
        }
    }

    private void printCampaignDetails() {
        for (Campaign campaign : campaigns.values()) {
            logger.info("Campaign Name: " + campaign.getCampaignName());
            logger.info("Twiml Instruction: " + campaign.getTwimlInstruction());
            logger.info("Number of Calls: " + campaign.getNumberOfCalls());
            logger.info("Call Details:");
            for (CallDetail callDetail : campaign.getCallDetails()) {
                logger.info(" - Session ID: " + callDetail.getSessionId());
                logger.info(" - Call SID: " + callDetail.getCallSid());
                logger.info(" - Transcript: " + callDetail.getTranscript());
                logger.info(" - Audio File Path: " + callDetail.getAudioFilePath());
                logger.info(" - Steps: " + callDetail.getSteps());
            }
        }
    }
}
