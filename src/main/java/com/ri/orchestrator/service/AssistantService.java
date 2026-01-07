package com.ri.orchestrator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ri.orchestrator.dto.AssistantResponse;
import com.ri.orchestrator.model.ConversationSession;
import com.ri.orchestrator.model.ConversationState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AssistantService {
  private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

  private static final String CONFIRMATION_KEYWORD = "confirmar";
  private static final String REJECT_KEYWORD = "no";
  private static final String DONE_KEYWORD = "listo";

  private final OllamaClient ollamaClient;
  private final SessionStore sessionStore;
  private final ObjectMapper objectMapper;

  public AssistantService(OllamaClient ollamaClient,
                          SessionStore sessionStore,
                          ObjectMapper objectMapper) {
    this.ollamaClient = ollamaClient;
    this.sessionStore = sessionStore;
    this.objectMapper = objectMapper;
  }

  public AssistantResponse handleMessage(String sessionId, String message) {
    String resolvedSessionId = resolveSessionId(sessionId);
    ConversationSession session = sessionStore.getOrCreate(resolvedSessionId);
    String replyText;
    boolean endSession = false;

    try {
      switch (session.getState()) {
        case START:
          changeState(session, ConversationState.CAPTURA_CLIENTE);
          replyText = buildAskClient();
          break;
        case CAPTURA_CLIENTE:
          if (isValidClient(message)) {
            session.getContext().put("cliente", message.trim());
            changeState(session, ConversationState.CAPTURA_ITEMS);
            replyText = buildAskItems();
          } else {
            replyText = buildAskClient();
          }
          break;
        case CAPTURA_ITEMS:
          if (isDone(message)) {
            changeState(session, ConversationState.RESUMEN);
            replyText = buildSummary(session.getContext());
          } else {
            addItem(session.getContext(), message);
            replyText = buildMoreItems();
          }
          break;
        case RESUMEN:
          changeState(session, ConversationState.CONFIRMACION);
          replyText = buildConfirmation(session.getContext());
          break;
        case CONFIRMACION:
          if (isConfirmed(message)) {
            changeState(session, ConversationState.SUCCESS);
            replyText = buildSuccess();
            endSession = true;
          } else if (isRejected(message)) {
            changeState(session, ConversationState.CAPTURA_ITEMS);
            replyText = buildAskItems();
          } else {
            replyText = buildConfirmation(session.getContext());
          }
          break;
        case SUCCESS:
          replyText = buildSuccess();
          endSession = true;
          break;
        case ERROR:
        default:
          replyText = buildError();
          endSession = true;
          break;
      }
    } catch (Exception ex) {
      log.error("Unexpected error processing session {}", resolvedSessionId, ex);
      changeState(session, ConversationState.ERROR);
      replyText = buildError();
      endSession = true;
    }

    if (endSession) {
      sessionStore.remove(session.getSessionId());
    } else {
      sessionStore.update(session);
    }

    return new AssistantResponse(
        session.getSessionId(),
        session.getState().name(),
        replyText,
        endSession
    );
  }

  private String resolveSessionId(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      return UUID.randomUUID().toString();
    }
    return sessionId.trim();
  }

  private void changeState(ConversationSession session, ConversationState nextState) {
    ConversationState previous = session.getState();
    if (previous != nextState) {
      log.info("Session {} state change: {} -> {}", session.getSessionId(), previous, nextState);
      session.setState(nextState);
    }
  }

  private boolean isValidClient(String message) {
    return message != null && !message.isBlank();
  }

  private boolean isDone(String message) {
    String normalized = normalize(message);
    return normalized.contains(DONE_KEYWORD) || normalized.contains("terminar");
  }

  private boolean isConfirmed(String message) {
    String normalized = normalize(message);
    return normalized.contains(CONFIRMATION_KEYWORD);
  }

  private boolean isRejected(String message) {
    String normalized = normalize(message);
    return normalized.equals(REJECT_KEYWORD) || normalized.contains("cancelar");
  }

  private String normalize(String message) {
    return message == null ? "" : message.toLowerCase().trim();
  }

  private void addItem(Map<String, Object> context, String itemText) {
    if (itemText == null || itemText.isBlank()) {
      return;
    }
    @SuppressWarnings("unchecked")
    List<String> items = (List<String>) context.computeIfAbsent("items", key -> new ArrayList<String>());
    items.add(itemText.trim());
  }

  private String buildAskClient() {
    return renderWithOllama(
        "Redacta una pregunta breve para pedir el cliente de una cotizacion. Responde solo la pregunta.",
        "Necesito el cliente para la cotizacion. ¿Cuál es?"
    );
  }

  private String buildAskItems() {
    return renderWithOllama(
        "Redacta una pregunta breve para pedir items de una cotizacion. Responde solo la pregunta.",
        "Decime el item y la cantidad."
    );
  }

  private String buildMoreItems() {
    return renderWithOllama(
        "Redacta una pregunta breve para pedir si hay mas items o si desea terminar.",
        "¿Querés agregar otro item o terminamos?"
    );
  }

  private String buildSummary(Map<String, Object> context) {
    String summaryPayload = serializeContext(context);
    return renderWithOllama(
        "Redacta un resumen breve para confirmacion basado en: " + summaryPayload,
        "Resumen de la solicitud: " + summaryPayload
    );
  }

  private String buildConfirmation(Map<String, Object> context) {
    String summaryPayload = serializeContext(context);
    return renderWithOllama(
        "Redacta una confirmacion breve para finalizar una cotizacion. Contexto: " + summaryPayload,
        "¿Confirmás esta acción? Respondé: CONFIRMAR"
    );
  }

  private String buildSuccess() {
    return renderWithOllama(
        "Redacta una confirmacion breve de que la solicitud fue registrada.",
        "Listo, la solicitud quedó registrada."
    );
  }

  private String buildError() {
    return "Ocurrió un error. Intentá nuevamente.";
  }

  private String renderWithOllama(String prompt, String fallback) {
    try {
      String response = ollamaClient.generate(prompt);
      if (response == null || response.isBlank()) {
        return fallback;
      }
      return response.trim();
    } catch (Exception ex) {
      log.warn("Ollama unavailable, using fallback response");
      return fallback;
    }
  }

  private String serializeContext(Map<String, Object> context) {
    Map<String, Object> safeContext = context == null ? new HashMap<>() : context;
    try {
      return objectMapper.writeValueAsString(safeContext);
    } catch (JsonProcessingException ex) {
      return safeContext.toString();
    }
  }
}
