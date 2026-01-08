package com.ri.orchestrator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ri.orchestrator.dto.OllamaGenerateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class OllamaClient {
  private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

  private final RestTemplate restTemplate;
  private final String baseUrl;
  private final String model;
  private final ObjectMapper objectMapper;

  public OllamaClient(RestTemplate restTemplate,
                      @Value("${ollama.base-url}") String baseUrl,
                      @Value("${ollama.model}") String model,
                      ObjectMapper objectMapper) {
    this.restTemplate = restTemplate;
    this.baseUrl = baseUrl;
    this.model = model;
    this.objectMapper = objectMapper;
  }

  public String generate(String prompt) {
    OllamaGenerateRequest request = new OllamaGenerateRequest(model, prompt, false);

    try {
      ResponseEntity<String> response = restTemplate.postForEntity(
          baseUrl + "/api/generate", request, String.class);

      if (!response.getStatusCode().is2xxSuccessful()) {
        log.warn("Ollama returned non-2xx status: {}", response.getStatusCode());
        throw new IllegalStateException("Ollama returned non-2xx status");
      }

      String body = response.getBody();
      if (body == null || body.isBlank()) {
        log.warn("Ollama response body is empty");
        throw new IllegalStateException("Ollama response body is empty");
      }

      String trimmed = body.trim();
      if (trimmed.startsWith("{")) {
        try {
          JsonNode root = objectMapper.readTree(trimmed);
          JsonNode responseNode = root.get("response");
          if (responseNode != null && !responseNode.isNull()) {
            String responseText = responseNode.asText();
            if (!responseText.isBlank()) {
              return responseText;
            }
          }
          log.warn("Ollama JSON response missing 'response' field, returning raw body");
          return body;
        } catch (Exception ex) {
          log.warn("Failed to parse Ollama JSON response, returning raw body");
          return body;
        }
      }

      return body;
    } catch (RestClientException ex) {
      log.warn("Ollama request failed: {}", ex.getMessage());
      throw new IllegalStateException("Ollama request failed");
    }
  }
}
