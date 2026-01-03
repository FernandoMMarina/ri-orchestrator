package com.ri.orchestrator.service;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class JobsServiceClient {
  private final RestTemplate restTemplate;
  private final String baseUrl;

  public JobsServiceClient(RestTemplate restTemplate,
                           @Value("${services.jobs.base-url}") String baseUrl) {
    this.restTemplate = restTemplate;
    this.baseUrl = baseUrl;
  }

  public String getJobs(Map<String, Object> data) {
    // Placeholder for real jobs-service call.
    try {
      // Example: restTemplate.getForObject(baseUrl + "/jobs", String.class);
      return "Consulta de trabajos enviada al jobs-service.";
    } catch (RestClientException ex) {
      throw new IllegalStateException("Jobs service request failed", ex);
    }
  }
}
