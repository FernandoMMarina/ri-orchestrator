package com.ri.orchestrator.dto;

import java.util.List;
import java.util.Map;

public class OllamaResponse {
  private String action;
  private Map<String, Object> data;
  private List<String> missing_fields;
  private String message;

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public Map<String, Object> getData() {
    return data;
  }

  public void setData(Map<String, Object> data) {
    this.data = data;
  }

  public List<String> getMissing_fields() {
    return missing_fields;
  }

  public void setMissing_fields(List<String> missing_fields) {
    this.missing_fields = missing_fields;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}
