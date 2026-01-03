package com.ri.orchestrator.service;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class OllamaClient {
  private final RestTemplate restTemplate;
  private final String baseUrl;
  private final String model;

  public OllamaClient(RestTemplate restTemplate,
                      @Value("${ollama.base-url}") String baseUrl,
                      @Value("${ollama.model}") String model) {
    this.restTemplate = restTemplate;
    this.baseUrl = baseUrl;
    this.model = model;
  }

  public String generateResponse(String prompt) {
    Map<String, Object> body = new HashMap<>();
    body.put("model", model);
    body.put("prompt", prompt);
    body.put("stream", false);

    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> response = restTemplate.postForObject(
          baseUrl + "/api/generate", body, Map.class);

      if (response == null || !response.containsKey("response")) {
        throw new IllegalStateException("Ollama response missing 'response' field");
      }

      Object raw = response.get("response");
      return raw == null ? null : raw.toString();
    } catch (RestClientException ex) {
      throw new IllegalStateException("Ollama request failed", ex);
    }
  }
}
