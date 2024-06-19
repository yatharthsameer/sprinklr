package com.example.demo.controller;

import com.example.demo.model.CallDetail;
import com.example.demo.model.Campaign;
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

//    private final String ACCOUNT_SID =
//    private final String AUTH_TOKEN =
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
            @RequestParam String campaignName,
            @RequestParam String to,
            @RequestParam String from,
            @RequestParam String twiml,
            @RequestParam int numberOfConcurrentCalls,
            @RequestParam String expectedTranscription) throws IOException {

        logger.info("Creating calls for campaign: " + campaignName);
        totalCalls = numberOfConcurrentCalls;
        completedCalls = new AtomicInteger(0);
        deferredResult = new DeferredResult<>();

        // Save TwiML to a file
        String filePath = "twiml_" + campaignName + ".txt";
        twiMLHandler.saveTwiMLToFile(twiml, filePath);

        Campaign campaign = new Campaign(campaignName, twiml, numberOfConcurrentCalls, expectedTranscription, filePath);
        campaigns.put(campaignName, campaign);
        campaignRepository.save(campaign); // Save initial campaign state

        for (int i = 0; i < numberOfConcurrentCalls; i++) {
            Call call = Call.creator(new PhoneNumber(to), new PhoneNumber(from), new Twiml(twiml))
                    .setMethod(HttpMethod.GET)
                    .setRecord(true)
                    .setStatusCallback("https://286a-182-69-183-158.ngrok-free.app/events")
                    .setStatusCallbackMethod(HttpMethod.POST)
                    .setStatusCallbackEvent(Arrays.asList("completed"))
                    .create();
            logger.info("Call initiated with SID: " + call.getSid());
            CallDetail callDetail = new CallDetail(null, call.getSid()); // Initialize with null sessionId
            campaign.addCallDetail(callDetail);
            callSidToCampaignMap.put(call.getSid(), campaignName); // Map callSid to campaignName
        }

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

    @GetMapping("/audio/{sessionId}")
    public ResponseEntity<byte[]> getAudio(@PathVariable String sessionId) {
        String filePath = getFilePathBySessionId(sessionId);
        if (filePath != null) {
            try {
                File file = new File(filePath);
                FileInputStream fileInputStream = new FileInputStream(file);
                byte[] audioBytes = fileInputStream.readAllBytes();
                fileInputStream.close();
                return ResponseEntity.ok(audioBytes);
            } catch (IOException e) {
                logger.severe("Failed to read audio file: " + e.getMessage());
                return ResponseEntity.status(500).build();
            }
        } else {
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

        // Read TwiML instructions from file
        List<String> twimlLines = twiMLHandler.readTwiMLFromFile(campaign.getTwimlFilePath());

        // Determine the next set of instructions to return
        int lastHandledGatherIndex = campaign.getLastHandledGatherIndex();
        StringBuilder responseTwiml = new StringBuilder();
        responseTwiml.append("<Response>\n");

        boolean foundGather = false;

        for (int i = lastHandledGatherIndex + 1; i < twimlLines.size(); i++) {
            String line = twimlLines.get(i);
            if (line.contains("<Gather")) {
                foundGather = true;
            }
            if (foundGather) {
                responseTwiml.append(line).append("\n");
            }
            if (line.contains("</Gather>")) {
                campaign.setLastHandledGatherIndex(i); // Update the last handled gather index
                break;
            }
        }

        responseTwiml.append("</Response>");

        // Save the campaign state
        campaignRepository.save(campaign);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(responseTwiml.toString());
    }

    private String getSessionIdByCallSid(String callSid) {
        for (Map.Entry<String, String> entry : sessionToCallMap.entrySet()) {
            if (entry.getValue().equals(callSid)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private String getFilePathBySessionId(String sessionId) {
        String callSid = sessionToCallMap.get(sessionId);
        if (callSid != null) {
            for (Campaign campaign : campaigns.values()) {
                for (CallDetail callDetail : campaign.getCallDetails()) {
                    if (callDetail.getCallSid().equals(callSid)) {
                        return callDetail.getAudioFilePath();
                    }
                }
            }
        }
        return null;
    }

    public void updateSessionId(String callSid, String sessionId) {
        sessionToCallMap.put(sessionId, callSid);
        String campaignName = callSidToCampaignMap.get(callSid);
        if (campaignName != null) {
            Campaign campaign = campaigns.get(campaignName);
            for (CallDetail callDetail : campaign.getCallDetails()) {
                if (callDetail.getCallSid().equals(callSid)) {
                    callDetail.setSessionId(sessionId);
                    break;
                }
            }
            campaignRepository.save(campaign); // Update campaign in MongoDB
        }
    }

    private void updateAudioFilePath(String callSid, String audioFilePath) {
        String campaignName = callSidToCampaignMap.get(callSid);
        if (campaignName != null) {
            Campaign campaign = campaigns.get(campaignName);
            for (CallDetail callDetail : campaign.getCallDetails()) {
                if (callDetail.getCallSid().equals(callSid)) {
                    callDetail.setAudioFilePath(audioFilePath);
                    break;
                }
            }
            campaignRepository.save(campaign); // Update campaign in MongoDB
        }
    }

    private void printCampaignDetails() {
        for (Campaign campaign : campaigns.values()) {
            logger.info("Campaign Name: " + campaign.getCampaignName());
            logger.info("Twiml Instruction: " + campaign.getTwimlInstruction());
            logger.info("Number of Calls: " + campaign.getNumberOfCalls());
            logger.info("Expected Transcription: " + campaign.getExpectedTranscription());
            logger.info("Call Details:");
            for (CallDetail callDetail : campaign.getCallDetails()) {
                logger.info(" - Session ID: " + callDetail.getSessionId());
                logger.info(" - Call SID: " + callDetail.getCallSid());
                logger.info(" - Transcript: " + callDetail.getTranscript());
                logger.info(" - audio: " + callDetail.getAudioFilePath());
            }
        }
    }
}
