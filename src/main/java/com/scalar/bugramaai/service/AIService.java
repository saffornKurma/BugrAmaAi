package com.scalar.bugramaai.service;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

@Service
public class AIService {
    private static final Logger logger = LoggerFactory.getLogger(AIService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private Process ollamaServeProcess;
    private Process mistralProcess;

    @PostConstruct
    public void startOllamaAndMistral() {
        try {
        // Check if Ollama is already running
        Process checkProcess = new ProcessBuilder("pgrep", "-f", "ollama").start();
        if (checkProcess.waitFor() == 0) {
            logger.info("Ollama is already running. Skipping startup.");
            return; // Skip starting a new instance
        }

            logger.info("Starting Ollama server...");
            ollamaServeProcess = new ProcessBuilder("ollama", "serve").start();
            Thread.sleep(5000); // Wait for the server to initialize

            logger.info("Starting Mistral AI model...");
            mistralProcess = new ProcessBuilder("ollama", "run", "mistral").start();

            logger.info("Ollama and Mistral started successfully!");
        } catch (IOException | InterruptedException e) {
            logger.error("Failed to start Ollama/Mistral", e);
        }
    }

    @PreDestroy
    public void stopOllamaAndMistral() {
        try {
            if (mistralProcess != null) {
                logger.info("Stopping Mistral...");
                mistralProcess.destroy();
                logger.info("Mistral stopped.");
            }

            if (ollamaServeProcess != null) {
                logger.info("Stopping Ollama server...");
                ollamaServeProcess.destroy();
                logger.info("Ollama server stopped.");
            }
        } catch (Exception e) {
            logger.error("Error while stopping Ollama/Mistral", e);
        }
    }

    private String extractMarket(String description) {
        if (description.contains("FRR")) return "FRR";
        if (description.contains("SBB")) return "SBB";
        if (description.contains("DBMCP")) return "DBMCP";
        return "Unknown";
    }

    private String getStandardInvestigationSteps(String market) {
        switch (market) {
            case "FRR":
                return "1️⃣ Check if issueContractRQ was sent from ProviderAO to the provider.\n"
                        + "2️⃣ Look for a successful issueContractRS response.\n"
                        + "3️⃣ If successful, validate issueTicketRQ format.\n"
                        + "4️⃣ Check reverse flow logs for the response.";
            case "SBB":
                return "1️⃣ Verify ticketing credentials.\n"
                        + "2️⃣ Ensure authTokenRQ was processed correctly.\n"
                        + "3️⃣ Look at issueTicketRQ and validate parameters.\n"
                        + "4️⃣ Confirm issueTicketRS response is as expected.";
            case "DBMCP":
                return "1️⃣ Ensure passenger profile is loaded.\n"
                        + "2️⃣ Verify issueTicketRQ has all mandatory fields.\n"
                        + "3️⃣ Check provider response logs.\n"
                        + "4️⃣ Debug error codes in issueTicketRS.";
            default:
                return "No predefined investigation steps. Perform standard debugging.";
        }
    }


    public String getAIResolution(String bugDescription) {
        try {
            URL url = new URL("http://localhost:11434/api/generate");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            // Extract Market from Description
            String market = extractMarket(bugDescription);

//            String inputJson = "{\"model\":\"mistral\",\"prompt\":\"Bug in " + market +
//                    ": " + bugDescription +
//                    ". How to investigate? Key checks: " + getStandardInvestigationSteps(market) + "\"}";


            String inputJson = "{\"model\":\"mistral\",\"prompt\":\"Bug: " + bugDescription + " can you answer it in five words?\"}";
            try (OutputStream os = conn.getOutputStream()) {
                os.write(inputJson.getBytes());
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                logger.info("Raw Mistral Response: {}", line);
                try {
                    JsonNode jsonNode = objectMapper.readTree(line);
                    if (jsonNode.has("response")) {
                        response.append(jsonNode.get("response").asText()).append(" ");
                    }
                    if (jsonNode.has("done") && jsonNode.get("done").asBoolean()) {
                        break;
                    }
                } catch (Exception e) {
                    logger.error("Error parsing JSON response: {}", line, e);
                }
            }
            br.close();
            return response.toString();
        } catch (Exception e) {
            return "AI resolution not available";
        }
    }
}
