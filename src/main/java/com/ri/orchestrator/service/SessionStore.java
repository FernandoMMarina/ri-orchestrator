package com.ri.orchestrator.service;

import com.ri.orchestrator.model.ConversationSession;
import com.ri.orchestrator.model.ConversationState;
import java.time.Instant;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class SessionStore {
  private final ConcurrentHashMap<String, ConversationSession> sessions = new ConcurrentHashMap<>();

  public ConversationSession getOrCreate(String sessionId) {
    return sessions.computeIfAbsent(sessionId, id ->
        new ConversationSession(id, ConversationState.START, new HashMap<>(), Instant.now()));
  }

  public void update(ConversationSession session) {
    session.setLastUpdated(Instant.now());
    sessions.put(session.getSessionId(), session);
  }

  public void remove(String sessionId) {
    sessions.remove(sessionId);
  }
}
