package com.example.demo.controller;

import com.twilio.http.HttpMethod;
import com.twilio.rest.api.v2010.account.Call;
import com.twilio.type.PhoneNumber;
import com.twilio.Twilio;
import com.twilio.type.Twiml;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

@RestController
public class CallController {

    private final String ACCOUNT_SID = "AC72617e7ffac0b0bf13e0e64302696797";
    private final String AUTH_TOKEN = "1686d9a777f4e815dc66a17f58d69188";
    private AtomicInteger completedCalls;
    private DeferredResult<ResponseEntity<Object>> deferredResult;
    private int totalCalls;
    private static final Logger logger = Logger.getLogger(CallController.class.getName());

    public CallController() {
        Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
    }

    @PostMapping("/createCalls")
    public DeferredResult<ResponseEntity<Object>> createCalls(
            @RequestParam String to,
            @RequestParam String from,
            @RequestParam String twiml,
            @RequestParam int numberOfConcurrentCalls) {
        logger.info("Creating calls");
        totalCalls = numberOfConcurrentCalls;
        completedCalls = new AtomicInteger(0);
        deferredResult = new DeferredResult<>();

        for (int i = 0; i < numberOfConcurrentCalls; i++) {
            Call call = Call.creator(new PhoneNumber(to), new PhoneNumber(from), new Twiml(twiml))
                    .setMethod(HttpMethod.GET)
                    .setStatusCallback("https://fc6b-182-69-177-247.ngrok-free.app/events")
                    .setStatusCallbackMethod(HttpMethod.POST)
                    .setStatusCallbackEvent(Arrays.asList("completed"))
                    .create();
            logger.info("Call initiated with SID: " + call.getSid());
        }

        return deferredResult;
    }

    @PostMapping("/events")
    public void handleStatusCallback(
            @RequestParam String CallSid,
            @RequestParam String CallStatus) {
        logger.info("Received status callback for call SID: " + CallSid + " with status: " + CallStatus);
        if ("completed".equals(CallStatus)) {
            if (completedCalls.incrementAndGet() == totalCalls) {
                Map<String, String> response = new HashMap<>();
                response.put("message", "All calls have completed");
                deferredResult.setResult(ResponseEntity.ok(response));
            }
        } else {
            logger.info("Received non-completed status: " + CallStatus + " for call SID: " + CallSid);
        }
    }
}