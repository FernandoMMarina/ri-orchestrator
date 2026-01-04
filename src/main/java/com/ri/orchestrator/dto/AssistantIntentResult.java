package com.ri.orchestrator.dto;

import java.util.List;
import java.util.Map;

public class AssistantIntentResult {
  private String intent;
  private Map<String, Object> data;
  private List<String> missing_fields;
  private boolean confirmation_required;
  private String message;

  public String getIntent() {
    return intent;
  }

  public void setIntent(String intent) {
    this.intent = intent;
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

  public boolean isConfirmation_required() {
    return confirmation_required;
  }

  public void setConfirmation_required(boolean confirmation_required) {
    this.confirmation_required = confirmation_required;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}
