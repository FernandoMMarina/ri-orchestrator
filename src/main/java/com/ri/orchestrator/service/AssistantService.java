package com.ri.orchestrator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ri.orchestrator.dto.AssistantResponse;
import com.ri.orchestrator.model.ConversationSession;
import com.ri.orchestrator.model.ConversationState;
import java.text.Normalizer;
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
          IntentType intent = classifyItemIntent(message);
          if (intent == IntentType.FINISH_ITEMS) {
            if (hasItems(session.getContext())) {
              log.info("Session {} transition CAPTURA_ITEMS -> RESUMEN", session.getSessionId());
              changeState(session, ConversationState.RESUMEN);
              replyText = buildSummary(session.getContext());
            } else {
              replyText = buildAskItems();
            }
          } else {
            addItem(session.getContext(), message);
            replyText = buildMoreItems();
          }
          break;
        case RESUMEN:
        case CONFIRMACION:
          if (isConfirmed(message)) {
            changeState(session, ConversationState.SUCCESS);
            replyText = buildSuccess();
            endSession = true;
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

  private IntentType classifyItemIntent(String message) {
    String normalized = normalize(message);
    if (normalized.isBlank()) {
      return IntentType.ADD_ITEM;
    }
    if (containsFinishKeyword(normalized)) {
      return IntentType.FINISH_ITEMS;
    }
    return IntentType.ADD_ITEM;
  }

  private boolean isConfirmed(String message) {
    String normalized = normalize(message);
    return normalized.contains("confirmo") || normalized.contains(CONFIRMATION_KEYWORD);
  }

  private boolean isRejected(String message) {
    String normalized = normalize(message);
    return normalized.equals(REJECT_KEYWORD) || normalized.contains("cancelar");
  }

  private String normalize(String message) {
    if (message == null) {
      return "";
    }
    String lower = message.toLowerCase().trim();
    String normalized = Normalizer.normalize(lower, Normalizer.Form.NFD);
    return normalized.replaceAll("\\p{M}", "");
  }

  private void addItem(Map<String, Object> context, String itemText) {
    if (itemText == null || itemText.isBlank()) {
      return;
    }
    @SuppressWarnings("unchecked")
    List<String> items = (List<String>) context.computeIfAbsent("items", key -> new ArrayList<String>());
    items.add(itemText.trim());
  }

  private boolean hasItems(Map<String, Object> context) {
    Object items = context == null ? null : context.get("items");
    if (!(items instanceof List<?>)) {
      return false;
    }
    return !((List<?>) items).isEmpty();
  }

  private boolean containsFinishKeyword(String normalized) {
    return normalized.contains(DONE_KEYWORD)
        || normalized.contains("terminar")
        || normalized.contains("terminamos")
        || normalized.contains("finalizar")
        || normalized.contains("cerrar")
        || normalized.contains("resumen")
        || normalized.contains("confirmar");
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
        "Redacta un resumen breve con pedido de confirmacion basado en: " + summaryPayload,
        "Resumen de la solicitud: " + summaryPayload + ". ¿Confirmás esta acción? Respondé: CONFIRMAR"
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

  private enum IntentType {
    ADD_ITEM,
    FINISH_ITEMS
  }
}
