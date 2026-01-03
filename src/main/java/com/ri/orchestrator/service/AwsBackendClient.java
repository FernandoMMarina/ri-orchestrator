package com.ri.orchestrator.service;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class AwsBackendClient {
  private final RestTemplate restTemplate;
  private final String baseUrl;
  private final String token;

  public AwsBackendClient(RestTemplate restTemplate,
                          @Value("${services.aws.base-url}") String baseUrl,
                          @Value("${services.aws.token}") String token) {
    this.restTemplate = restTemplate;
    this.baseUrl = baseUrl;
    this.token = token;
  }

  public String createQuote(Map<String, Object> data) {
    // Placeholder for real AWS backend call.
    try {
      // Example: restTemplate.postForObject(baseUrl + "/quotes", buildRequest(data), String.class);
      return "Solicitud de cotizacion enviada al backend AWS.";
    } catch (RestClientException ex) {
      throw new IllegalStateException("AWS backend request failed", ex);
    }
  }
}
