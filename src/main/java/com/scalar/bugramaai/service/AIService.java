package com.scalar.bugramaai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

@Service
public class AIService {
    private static final Logger logger = LoggerFactory.getLogger(AIService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private Process ollamaServeProcess;
    private Process mistralProcess;
    private Process flaskProcess;

    @PostConstruct
    public void startAIComponents() {
        try {
            logger.info("🔄 Checking if Ollama is running...");
            if (!isProcessRunning("ollama serve")) {
                logger.info("🚀 Starting Ollama...");
                ollamaServeProcess = startProcess("ollama serve");
            }

            logger.info("🔍 Checking if Mistral model is running...");
            if (!isProcessRunning("ollama run mistral")) {
                logger.info("🚀 Starting Mistral AI model...");
                mistralProcess = startProcess("ollama run mistral");
            }

            logger.info("🔍 Checking if Flask FAISS server is running...");
            if (!isProcessRunning("flask run")) {
                logger.info("🚀 Starting Flask FAISS server...");
                flaskProcess = startProcess("source mistral_env/bin/activate && flask run --host=0.0.0.0 --port=5000");
            }

            logger.info("✅ Ollama, Mistral, and Flask FAISS started successfully!");
        } catch (Exception e) {
            logger.error("❌ Error starting AI components", e);
        }
    }

    @PreDestroy
    public void stopAIComponents() {
        logger.info("🛑 Stopping AI components...");
        stopProcess(ollamaServeProcess, "Ollama");
        stopProcess(mistralProcess, "Mistral");
        stopProcess(flaskProcess, "Flask FAISS");
        logger.info("✅ AI components stopped.");
    }

    private Process startProcess(String command) throws IOException {
        return new ProcessBuilder("bash", "-c", command).start();
    }

    private void stopProcess(Process process, String name) {
        if (process != null) {
            process.destroy();
            logger.info("🛑 {} stopped.", name);
        }
    }

    private boolean isProcessRunning(String processName) {
        try {
            Process process = new ProcessBuilder("bash", "-c", "pgrep -f \"" + processName + "\"").start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            return reader.readLine() != null;  // If there's output, process is running
        } catch (IOException e) {
            return false;
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

    public String searchBugInFaiss(String bugDescription) {
        try {
            String url = "http://localhost:8383/search";  // FAISS Python API

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("query", bugDescription);

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            return response.getBody();
        } catch (Exception e) {
            logger.error("❌ Error searching in FAISS: {}", e.getMessage());
            return "FAISS search failed";
        }
    }

    public String getAIResolution(String bugDescription) {
        try {
            // 🔍 Step 1: Search in FAISS first
            String faissResponse = searchBugInFaiss(bugDescription);
            logger.info("🔍 FAISS Search Response: {}", faissResponse);

            // 🔍 Step 2: If FAISS returns a relevant bug, return it
            if (!faissResponse.contains("FAISS search failed")) {
                return "🔍 FAISS Suggested Related Issues: \n" + faissResponse;
            }

            // 🔍 Step 3: If FAISS fails, proceed with Mistral AI
            URL url = new URL("http://localhost:11434/api/generate");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            // Extract Market from Description
            String market = extractMarket(bugDescription);

            String inputJson = "{\"model\":\"mistral\",\"prompt\":\"Bug: " + bugDescription + " How to investigate? Key checks: " + getStandardInvestigationSteps(market) + "\"}";
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
            logger.error("❌ Error in AI resolution: {}", e.getMessage());
            return "AI resolution not available";
        }
    }
}
