package com.example.demo.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.logging.Logger;

@Controller
public class WebController {

    private static final Logger logger = Logger.getLogger(WebController.class.getName());

    @GetMapping("/")
    public String home() {
        logger.info("Serving home page");
        return "index";  // Return your HTML page name if using Thymeleaf or JSP
    }

    @PostMapping("/")
    public ResponseEntity<String> startStream(@RequestHeader("host") String host)  {
        logger.info("Starting stream");
        String twiml = "<Response>\n" +
                "  <Start>\n" +
                "    <Stream track=\"both_tracks\" url=\"wss://" + host + "/ws\">\n" +
                "      <Parameter name=\"streamSid\" value=\"example-streamSid\" />\n" +
                "    </Stream>\n" +
                "  </Start>\n" +
                "  <Pause length=\"8\" />\n" +
                "  <Play>https://indigo-badger-5268.twil.io/assets/Uniqlo.wav</Play>\n" +
                "  <Pause length=\"10\" />\n" +
                "</Response>";

        logger.info("Generated TwiML: " + twiml);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(twiml);
    }
}