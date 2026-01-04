package com.ri.orchestrator.service;

import com.ri.orchestrator.dto.OllamaGenerateRequest;
import com.ri.orchestrator.dto.OllamaGenerateResponse;
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

  public OllamaClient(RestTemplate restTemplate,
                      @Value("${ollama.base-url}") String baseUrl,
                      @Value("${ollama.model}") String model) {
    this.restTemplate = restTemplate;
    this.baseUrl = baseUrl;
    this.model = model;
  }

  public String generate(String prompt) {
    OllamaGenerateRequest request = new OllamaGenerateRequest(model, prompt, false);

    try {
      ResponseEntity<OllamaGenerateResponse> response = restTemplate.postForEntity(
          baseUrl + "/api/generate", request, OllamaGenerateResponse.class);

      if (!response.getStatusCode().is2xxSuccessful()) {
        log.error("Ollama returned non-2xx status: {}", response.getStatusCode());
        throw new IllegalStateException("Ollama returned non-2xx status");
      }

      OllamaGenerateResponse body = response.getBody();
      if (body == null || body.getResponse() == null || body.getResponse().isBlank()) {
        log.error("Ollama response missing 'response' field");
        throw new IllegalStateException("Ollama response missing 'response' field");
      }

      return body.getResponse();
    } catch (RestClientException ex) {
      log.error("Ollama request failed", ex);
      throw new IllegalStateException("Ollama request failed", ex);
    }
  }
}
