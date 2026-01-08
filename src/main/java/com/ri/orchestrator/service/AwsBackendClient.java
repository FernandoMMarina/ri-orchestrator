package com.ri.orchestrator.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;

@Component
public class AwsBackendClient {
  private final RestClient restClient;
  private final String baseUrl;
  private final String serviceToken;

  public AwsBackendClient(RestClient restClient,
                          @Value("${aws.backend.base-url}") String baseUrl,
                          @Value("${aws.backend.service-token}") String serviceToken) {
    if (serviceToken == null || serviceToken.isBlank()) {
      throw new IllegalStateException("Missing aws.backend.service-token");
    }
    this.restClient = restClient;
    this.baseUrl = baseUrl;
    this.serviceToken = serviceToken;
  }

  public Map<String, Object> getUserById(String userId) {
    return getForObject("/users/user/{id}", userId);
  }

  public Map<String, Object> getSucursalById(String sucursalId) {
    return getForObject("/sucursals/{id}", sucursalId);
  }

  public List<Map<String, Object>> searchUsersByName(String name) {
    try {
      List<Map<String, Object>> response = restClient.get()
          .uri(baseUrl + "/users/search?name={name}", name)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
          .retrieve()
          .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
      return response == null ? Collections.emptyList() : response;
    } catch (HttpClientErrorException.NotFound ex) {
      return Collections.emptyList();
    } catch (RestClientException ex) {
      throw new IllegalStateException("AWS backend request failed", ex);
    }
  }

  private Map<String, Object> getForObject(String path, String id) {
    try {
      return restClient.get()
          .uri(baseUrl + path, id)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
          .retrieve()
          .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    } catch (HttpClientErrorException.NotFound ex) {
      return Map.of();
    } catch (RestClientException ex) {
      throw new IllegalStateException("AWS backend request failed", ex);
    }
  }
}
