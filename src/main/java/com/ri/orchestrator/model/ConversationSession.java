package com.ri.orchestrator.model;

import java.time.Instant;
import java.util.Map;

public class ConversationSession {
  private final String sessionId;
  private ConversationState state;
  private Map<String, Object> context;
  private Instant lastUpdated;

  public ConversationSession(String sessionId, ConversationState state,
                             Map<String, Object> context, Instant lastUpdated) {
    this.sessionId = sessionId;
    this.state = state;
    this.context = context;
    this.lastUpdated = lastUpdated;
  }

  public String getSessionId() {
    return sessionId;
  }

  public ConversationState getState() {
    return state;
  }

  public void setState(ConversationState state) {
    this.state = state;
  }

  public Map<String, Object> getContext() {
    return context;
  }

  public void setContext(Map<String, Object> context) {
    this.context = context;
  }

  public Instant getLastUpdated() {
    return lastUpdated;
  }

  public void setLastUpdated(Instant lastUpdated) {
    this.lastUpdated = lastUpdated;
  }
}
