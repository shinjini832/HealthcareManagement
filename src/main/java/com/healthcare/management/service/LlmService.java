package com.healthcare.management.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class LlmService {

    private final RestTemplate restTemplate;

    @Value("${llm.api-key}")
    private String apiKey;

    @Value("${llm.api-url}")
    private String apiUrl;

    public LlmService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    }

    public String generatePreVisitSummary(String symptoms) {
        String prompt = "Analyse these symptoms and return: urgency level (Low / Medium / High), chief complaint, and three suggested questions for the doctor. Symptoms: " + symptoms;
        return callLlmApi(prompt, symptoms);
    }

    public String generatePostVisitSummary(String notes) {
        String prompt = "Convert these clinical notes into a patient-friendly summary with medication schedule and follow-up steps: " + notes;
        return callLlmApi(prompt, notes);
    }

    private String callLlmApi(String prompt, String fallbackInput) {
        if ("mock-key".equalsIgnoreCase(apiKey) || apiKey == null || apiKey.isEmpty()) {
            log.warn("LLM API key is unconfigured or set to mock-key. Returning fallback raw input.");
            return fallbackInput;
        }

        try {
            String url = apiUrl + "?key=" + apiKey;

            Map<String, Object> part = new HashMap<>();
            part.put("text", prompt);

            Map<String, Object> content = new HashMap<>();
            content.put("parts", List.of(part));

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("contents", List.of(content));

            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestBody, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) body.get("candidates");
                if (candidates != null && !candidates.isEmpty()) {
                    Map<String, Object> candidate = candidates.get(0);
                    Map<String, Object> contentObj = (Map<String, Object>) candidate.get("content");
                    if (contentObj != null) {
                        List<Map<String, Object>> parts = (List<Map<String, Object>>) contentObj.get("parts");
                        if (parts != null && !parts.isEmpty()) {
                            String text = (String) parts.get(0).get("text");
                            if (text != null && !text.trim().isEmpty()) {
                                return text;
                            }
                        }
                    }
                }
            }
            log.warn("LLM API response did not contain expected text content. Returning fallback raw input.");
            return fallbackInput;
        } catch (Exception e) {
            log.warn("Error occurred calling LLM API (timeout or connectivity): {}. Returning fallback raw input.", e.getMessage());
            return fallbackInput;
        }
    }
}
