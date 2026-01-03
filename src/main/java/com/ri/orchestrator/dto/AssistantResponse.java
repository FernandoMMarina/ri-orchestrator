package com.ri.orchestrator.dto;

public class AssistantResponse {
  private String reply;

  public AssistantResponse() {}

  public AssistantResponse(String reply) {
    this.reply = reply;
  }

  public String getReply() {
    return reply;
  }

  public void setReply(String reply) {
    this.reply = reply;
  }
}
