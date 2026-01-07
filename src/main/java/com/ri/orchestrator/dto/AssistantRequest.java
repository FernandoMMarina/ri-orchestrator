package com.ri.orchestrator.dto;

import jakarta.validation.constraints.NotBlank;

public class AssistantRequest {
  @NotBlank
  private String message;
  private String sessionId;

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }
}
