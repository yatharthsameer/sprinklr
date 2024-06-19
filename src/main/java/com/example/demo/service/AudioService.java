package com.example.demo.service;

import org.springframework.stereotype.Service;

import java.io.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class AudioService {

    private static final Logger logger = Logger.getLogger(AudioService.class.getName());
    private ConcurrentHashMap<String, RandomAccessFile> files = new ConcurrentHashMap<>();

    // Recording Methods
    public String startRecording(String sessionId) throws IOException {
        String fileName = createRecordingFileName(sessionId);
        RandomAccessFile file = new RandomAccessFile(fileName, "rw");
        files.put(sessionId, file);
        writeWavHeader(file);
        logger.info("Recording started: " + fileName);
        return fileName;
    }

    public void writeData(String sessionId, byte[] data) throws IOException {
        RandomAccessFile file = files.get(sessionId);
        if (file != null) {
            file.write(data);
//            logger.info("Data written for session: " + sessionId);
        } else {
            logger.warning("No file found for session: " + sessionId);
        }
    }

    public void stopRecording(String sessionId) throws IOException {
        RandomAccessFile file = files.remove(sessionId);
        if (file != null) {
            updateWavFileSize(file);
            file.close();
            logger.info("Recording file closed for session: " + sessionId);
        } else {
            logger.warning("No file found for session: " + sessionId);
        }
    }

    // Transcription Methods
    public String createTranscriptionFile(String sessionId) throws IOException {
        String fileName = createTranscriptionFileName(sessionId);
        File file = new File(fileName);
        if (file.createNewFile()) {
            logger.info("Transcription file created: " + fileName);
        } else {
            logger.warning("Failed to create transcription file: " + fileName);
        }
        return fileName;
    }

    public void writeTranscription(String fileName, String transcript) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName, true))) {
            writer.write(transcript);
            writer.newLine();
            logger.info("Transcription written to file: " + fileName + " | Transcript: " + transcript);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to write transcription to file: " + fileName, e);
            throw e;
        }
    }

    // Helper Methods
    private String createRecordingFileName(String sessionId) {
        return "recording_" + sessionId + ".wav";
    }

    private String createTranscriptionFileName(String sessionId) {
        return "transcription_" + sessionId + ".txt";
    }

    private void writeWavHeader(RandomAccessFile file) throws IOException {
        byte[] header = new byte[]{
                // RIFF header
                0x52, 0x49, 0x46, 0x46, // "RIFF"
                0x00, 0x00, 0x00, 0x00, // Placeholder for file size
                0x57, 0x41, 0x56, 0x45, // "WAVE"

                // fmt subchunk
                0x66, 0x6D, 0x74, 0x20, // "fmt "
                0x10, 0x00, 0x00, 0x00, // 16 for PCM
                0x07, 0x00,             // Audio format = 7 (μ-law)
                0x01, 0x00,             // Number of channels = 1
                0x40, 0x1F, 0x00, 0x00, // Sample rate = 8000
                (byte) 0x80, 0x3E, 0x00, 0x00, // Byte rate = 16000 (SampleRate * NumChannels * BitsPerSample/8)
                0x02, 0x00,             // Block align = 2 (NumChannels * BitsPerSample/8)
                0x08, 0x00,             // Bits per sample = 8

                // data subchunk
                0x64, 0x61, 0x74, 0x61, // "data"
                0x00, 0x00, 0x00, 0x00  // Placeholder for subchunk2 size
        };
        file.write(header);
    }

    private void updateWavFileSize(RandomAccessFile file) throws IOException {
        long fileSize = file.length();
        long dataSize = fileSize - 44; // Adjust the header size

        file.seek(4);
        file.write(intToByteArray((int) (fileSize - 8)));

        file.seek(40);
        file.write(intToByteArray((int) dataSize));
    }

    private byte[] intToByteArray(int value) {
        return new byte[]{
                (byte) (value & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 24) & 0xFF),
        };
    }
}
