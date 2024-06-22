package com.example.demo.service;

import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

@Service
public class TwiMLHandler {

    private static final Logger logger = Logger.getLogger(TwiMLHandler.class.getName());

    public void saveTwiMLToFile(String twiml, String filePath) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write(twiml);
            logger.info("TwiML saved to file: " + filePath);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save TwiML to file: " + filePath, e);
        }
    }


    public List<String> readTwiMLFromFile(String filePath) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            logger.info("TwiML read from file: " + filePath);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to read TwiML from file: " + filePath, e);
        }
        return lines;
    }
}
