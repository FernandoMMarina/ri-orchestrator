package com.ri.orchestrator.dto;

import jakarta.validation.constraints.NotBlank;

public class AssistantRequest {
  @NotBlank
  private String message;

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}
