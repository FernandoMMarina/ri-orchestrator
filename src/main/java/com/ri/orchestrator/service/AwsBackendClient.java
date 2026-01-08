package com.ri.orchestrator.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

@Component
public class AwsBackendClient {
  private static final Logger log = LoggerFactory.getLogger(AwsBackendClient.class);
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
    boolean authHeaderPresent = serviceToken != null && !serviceToken.isBlank();
    log.info("AWS user search request: authHeaderPresent={}, name='{}'", authHeaderPresent, name);
    log.info("AWS user search token: {}", serviceToken);
    try {
      List<Map<String, Object>> response = restClient.get()
          .uri(baseUrl + "/users/users/search?name={name}&role=user", name)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
          .retrieve()
          .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
      return response == null ? Collections.emptyList() : response;
    } catch (HttpClientErrorException.NotFound ex) {
      log.info("AWS user search response: status=404, body='{}'", ex.getResponseBodyAsString());
      return Collections.emptyList();
    } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden ex) {
      log.warn("AWS user search auth failed: status={}, body='{}'",
          ex.getStatusCode(), ex.getResponseBodyAsString());
      return null;
    } catch (HttpServerErrorException ex) {
      log.warn("AWS user search server error: status={}, body='{}'",
          ex.getStatusCode(), ex.getResponseBodyAsString());
      return null;
    } catch (ResourceAccessException ex) {
      log.warn("AWS user search network error: {}", ex.getMessage());
      return null;
    } catch (HttpStatusCodeException ex) {
      log.warn("AWS user search error: status={}, body='{}'",
          ex.getStatusCode(), ex.getResponseBodyAsString());
      return null;
    } catch (RestClientException ex) {
      log.warn("AWS user search unexpected error: {}", ex.getMessage());
      return null;
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
