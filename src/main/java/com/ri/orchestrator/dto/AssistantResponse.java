package com.ri.orchestrator.dto;

public class AssistantResponse {
  private String session_id;
  private String state;
  private String reply_text;
  private boolean end_session;

  public AssistantResponse() {}

  public AssistantResponse(String session_id, String state, String reply_text, boolean end_session) {
    this.session_id = session_id;
    this.state = state;
    this.reply_text = reply_text;
    this.end_session = end_session;
  }

  public String getSession_id() {
    return session_id;
  }

  public void setSession_id(String session_id) {
    this.session_id = session_id;
  }

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  public String getReply_text() {
    return reply_text;
  }

  public void setReply_text(String reply_text) {
    this.reply_text = reply_text;
  }

  public boolean isEnd_session() {
    return end_session;
  }

  public void setEnd_session(boolean end_session) {
    this.end_session = end_session;
  }
}
