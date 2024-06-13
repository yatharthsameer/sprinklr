package com.example.demo.websocket;

import com.example.demo.service.AudioService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class TwilioWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = Logger.getLogger(TwilioWebSocketHandler.class.getName());
    private static  AudioService audioService = null;
    private final ConcurrentMap<String, SessionData> sessionDataMap = new ConcurrentHashMap<>();

    @Autowired
    public TwilioWebSocketHandler(AudioService audioService) {
        TwilioWebSocketHandler.audioService = audioService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        logger.info("Connection established: " + session.getId());
    }

    @Override
    public void handleTextMessage(@NotNull WebSocketSession session, TextMessage message) throws Exception {
        JsonNode jsonNode = new ObjectMapper().readTree(message.getPayload());
        String event = jsonNode.get("event").asText();
        String sessionId = session.getId();

        switch (event) {
            case "start":
                handleStartEvent(sessionId);
                break;
            case "media":
                handleMediaEvent(sessionId, jsonNode);
                break;
            case "stop":
                handleStopEvent(sessionId);
                break;
        }
    }

    private void handleStartEvent(String sessionId) throws IOException {
        initializeSpeechClient(sessionId);
        audioService.startRecording(sessionId);
        startTranscription(sessionId);
    }

    private void handleMediaEvent(String sessionId, JsonNode jsonNode) throws IOException {
        String payload = jsonNode.path("media").path("payload").asText();
        byte[] data = Base64.getDecoder().decode(payload);
        audioService.writeData(sessionId, data);
        sendToTranscriptionStream(sessionId, data);
    }

    private void handleStopEvent(String sessionId) throws IOException {
        audioService.stopRecording(sessionId);
        stopTranscription(sessionId);
    }

    private void initializeSpeechClient(String sessionId) throws IOException {
        String credentialsPath = "src/main/resources/keys/sa.json";
        GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream(credentialsPath));
        SpeechSettings speechSettings = SpeechSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                .build();
        SpeechClient speechClient = SpeechClient.create(speechSettings);
        sessionDataMap.put(sessionId, new SessionData(speechClient));
        logger.info("SpeechClient initialized for session: " + sessionId);
    }

    private void startTranscription(String sessionId) throws IOException {
        SessionData sessionData = sessionDataMap.get(sessionId);
        if (sessionData == null) return;

        StreamingRecognitionConfig config = StreamingRecognitionConfig.newBuilder()
                .setConfig(RecognitionConfig.newBuilder()
                        .setEncoding(RecognitionConfig.AudioEncoding.MULAW)
                        .setSampleRateHertz(8000)
                        .setLanguageCode("en-US")
                        .build())
                .build();

        ClientStream<StreamingRecognizeRequest> clientStream = sessionData.getSpeechClient().streamingRecognizeCallable().splitCall(sessionData.getResponseObserver());
        clientStream.send(StreamingRecognizeRequest.newBuilder().setStreamingConfig(config).build());
        sessionData.setClientStream(clientStream);

        String transcriptionFileName = audioService.createTranscriptionFile(sessionId);
        sessionData.setTranscriptionFileName(transcriptionFileName);

        logger.info("Started transcription for session: " + sessionId);
    }

    private void sendToTranscriptionStream(String sessionId, byte[] data) {
        SessionData sessionData = sessionDataMap.get(sessionId);
        if (sessionData == null || sessionData.getClientStream() == null) return;

        StreamingRecognizeRequest request = StreamingRecognizeRequest.newBuilder()
                .setAudioContent(ByteString.copyFrom(data))
                .build();
        sessionData.getClientStream().send(request);
        logger.info("Sent audio data to transcription stream for session: " + sessionId);
    }

    private void stopTranscription(String sessionId) throws IOException {
        SessionData sessionData = sessionDataMap.remove(sessionId);
        if (sessionData == null) return;

        ClientStream<StreamingRecognizeRequest> clientStream = sessionData.getClientStream();
        if (clientStream != null) {
            clientStream.closeSend();
        }

        SpeechClient speechClient = sessionData.getSpeechClient();
        if (speechClient != null) {
            speechClient.close();
        }

        logger.info("Stopped transcription and closed SpeechClient for session: " + sessionId);
    }

    private static class SessionData {
        @Getter
        private final SpeechClient speechClient;
        @Getter
        @Setter
        private ClientStream<StreamingRecognizeRequest> clientStream;
        @Getter
        private final ResponseObserver<StreamingRecognizeResponse> responseObserver;
        @Setter
        private String transcriptionFileName;

        public SessionData(SpeechClient speechClient) {
            this.speechClient = speechClient;
            this.responseObserver = new ResponseObserver<StreamingRecognizeResponse>() {
                @Override
                public void onStart(StreamController controller) {
                    logger.info("Transcription stream started");
                }

                @Override
                public void onResponse(StreamingRecognizeResponse response) {
                    if (!response.getResultsList().isEmpty()) {
                        String transcript = response.getResultsList().get(0).getAlternativesList().get(0).getTranscript();
                        try {
                            audioService.writeTranscription(transcriptionFileName, transcript);
                            logger.info("Received transcription: " + transcript);
                        } catch (IOException e) {
                            logger.log(Level.SEVERE, "Failed to write transcription", e);
                        }
                    }
                }

                @Override
                public void onComplete() {
                    logger.info("Transcription stream completed");
                }

                @Override
                public void onError(Throwable t) {
                    logger.log(Level.SEVERE, "Transcription stream error", t);
                }
            };
        }

    }
}
