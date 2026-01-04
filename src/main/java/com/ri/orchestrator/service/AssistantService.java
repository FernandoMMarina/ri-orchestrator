package com.ri.orchestrator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ri.orchestrator.dto.AssistantIntentResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AssistantService {
  private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

  private static final String SYSTEM_PROMPT =
      "Sos un asistente interno para cotizaciones de Rosenstein Instalaciones. "
          + "Solo guias y validas informacion; NO ejecutas acciones ni llamas endpoints. "
          + "No inventes datos ni IDs. Si falta informacion, pedi exactamente lo que falta. "
          + "Solo una accion por respuesta. Antes de cualquier accion que cree, actualice o elimine informacion, "
          + "siempre pedi confirmacion explicita preguntando: \u00bfConfirmas esta accion? y aclarando: "
          + "Responde exactamente CONFIRMAR para continuar. Responde siempre en JSON.\n\n"
          + "INTENTS PERMITIDOS: CREATE_QUOTE, UPDATE_QUOTE, APPROVE_QUOTE, DELETE_QUOTE.\n"
          + "OUTPUT SCHEMA:\n"
          + "{\n"
          + "  \"intent\": \"INTENT_NAME\",\n"
          + "  \"data\": {},\n"
          + "  \"missing_fields\": [],\n"
          + "  \"confirmation_required\": true,\n"
          + "  \"message\": \"Mensaje claro para el admin\"\n"
          + "}\n\n"
          + "Nunca inventes campos requeridos. Nunca calcules montos. Nunca asumas IDs.";

  private static final String CONFIRMATION_TEXT =
      "¿Confirmás esta acción? Respondé exactamente: CONFIRMAR";

  private static final Map<String, IntentDefinition> INTENT_DEFINITIONS = Map.of(
      "CREATE_QUOTE",
      new IntentDefinition(
          List.of("nombreTrabajo", "clienteId|clienteManual.nombre", "sucursalId", "ubicacion.direccion"),
          Set.of("clienteId", "sucursalId", "maquinaId", "totalCost", "totalIva", "numeroCotizacion", "jobNumber"),
          true),
      "UPDATE_QUOTE",
      new IntentDefinition(
          List.of("cotizacionId", "al_menos_un_campo_de_actualizacion"),
          Set.of("cotizacionId", "clienteId", "maquinaId", "asignadoA", "totalCost", "totalIva", "numeroFactura"),
          true),
      "APPROVE_QUOTE",
      new IntentDefinition(
          List.of("cotizacionId", "ubicacion.direccion"),
          Set.of("cotizacionId", "tipoDeTrabajo", "fechaTrabajo"),
          true),
      "DELETE_QUOTE",
      new IntentDefinition(
          List.of("cotizacionId"),
          Set.of("cotizacionId"),
          true)
  );

  private static final Set<String> UPDATE_FIELDS = Set.of(
      "nombreTrabajo",
      "descripcionTrabajo",
      "clienteId",
      "clienteManual",
      "maquinaId",
      "items",
      "totalCost",
      "totalIva",
      "estado",
      "asignadoA",
      "firma",
      "cuit",
      "razonSocial",
      "direccion",
      "correoContacto",
      "numeroFactura",
      "manoDeObra",
      "descripcionManoObra",
      "materiales",
      "equipos",
      "extras",
      "encargado",
      "aprobado"
  );


  private final OllamaClient ollamaClient;
  private final ObjectMapper objectMapper;

  public AssistantService(OllamaClient ollamaClient,
                          ObjectMapper objectMapper) {
    this.ollamaClient = ollamaClient;
    this.objectMapper = objectMapper;
  }

  public String handleMessage(String message) {
    String prompt = buildPrompt(message);
    String rawResponse;
    try {
      rawResponse = ollamaClient.generate(prompt);
    } catch (IllegalStateException ex) {
      log.error("Ollama call failed", ex);
      return buildErrorResponse("No se pudo contactar a la IA local.");
    }

    if (rawResponse == null || rawResponse.isBlank()) {
      return buildErrorResponse("No se recibio respuesta de la IA.");
    }

    AssistantIntentResult aiResponse = null;
    try {
      if (rawResponse.trim().startsWith("{")) {
        aiResponse = objectMapper.readValue(rawResponse, AssistantIntentResult.class);
      }
    } catch (JsonProcessingException ex) {
      log.error("Invalid JSON from Ollama: {}", rawResponse, ex);
    }

    String intent = normalizeIntent(aiResponse);
    Map<String, Object> data = aiResponse != null && aiResponse.getData() != null
        ? aiResponse.getData()
        : new HashMap<>();

    if (!INTENT_DEFINITIONS.containsKey(intent)) {
      intent = detectIntentFromMessage(message);
    }

    IntentDefinition definition = INTENT_DEFINITIONS.get(intent);
    List<String> missingFields = computeMissingFields(intent, definition, data);
    boolean confirmationRequired = definition.confirmationRequired && missingFields.isEmpty();
    String messageOut = buildMessage(intent, data, missingFields, confirmationRequired);

    AssistantIntentResult result = new AssistantIntentResult();
    result.setIntent(intent);
    result.setData(data);
    result.setMissing_fields(missingFields);
    result.setConfirmation_required(confirmationRequired);
    result.setMessage(messageOut);

    try {
      return objectMapper.writeValueAsString(result);
    } catch (JsonProcessingException ex) {
      log.error("Failed to serialize assistant response", ex);
      return buildErrorResponse("Error al construir la respuesta del asistente.");
    }
  }

  private String buildPrompt(String message) {
    return SYSTEM_PROMPT + "\n\nUsuario: \"" + message + "\"";
  }

  private String normalizeIntent(AssistantIntentResult aiResponse) {
    if (aiResponse == null || aiResponse.getIntent() == null) {
      return "";
    }
    return aiResponse.getIntent().toUpperCase(Locale.ROOT).trim();
  }

  private String detectIntentFromMessage(String message) {
    String text = message == null ? "" : message.toLowerCase(Locale.ROOT);
    if (text.contains("aprobar")) {
      return "APPROVE_QUOTE";
    }
    if (text.contains("eliminar") || text.contains("borrar")) {
      return "DELETE_QUOTE";
    }
    if (text.contains("actualizar") || text.contains("modificar") || text.contains("editar")) {
      return "UPDATE_QUOTE";
    }
    return "CREATE_QUOTE";
  }

  private List<String> computeMissingFields(String intent, IntentDefinition definition, Map<String, Object> data) {
    List<String> missing = new ArrayList<>();
    for (String required : definition.requiredFields) {
      if ("al_menos_un_campo_de_actualizacion".equals(required)) {
        if (!hasAnyUpdateField(data)) {
          missing.add(required);
        }
        continue;
      }
      if (!hasRequiredField(data, required)) {
        missing.add(required);
      }
    }
    return missing;
  }

  private boolean hasAnyUpdateField(Map<String, Object> data) {
    for (String field : UPDATE_FIELDS) {
      if (data.containsKey(field) && isNonEmptyValue(data.get(field))) {
        return true;
      }
    }
    return false;
  }

  private boolean hasRequiredField(Map<String, Object> data, String field) {
    if (field.contains("|")) {
      String[] options = field.split("\\|");
      for (String option : options) {
        if (hasRequiredField(data, option)) {
          return true;
        }
      }
      return false;
    }

    if (field.contains(".")) {
      String[] parts = field.split("\\.");
      Object current = data.get(parts[0]);
      for (int i = 1; i < parts.length; i++) {
        if (!(current instanceof Map)) {
          return false;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) current;
        current = map.get(parts[i]);
      }
      return isNonEmptyValue(current);
    }

    return data.containsKey(field) && isNonEmptyValue(data.get(field));
  }

  private boolean isNonEmptyValue(Object value) {
    if (value == null) {
      return false;
    }
    if (value instanceof String) {
      return !((String) value).isBlank();
    }
    return true;
  }

  private String buildMessage(String intent, Map<String, Object> data,
                              List<String> missingFields, boolean confirmationRequired) {
    if (!missingFields.isEmpty()) {
      return buildMissingFieldQuestion(intent, missingFields.get(0));
    }

    String summary;
    try {
      summary = objectMapper.writeValueAsString(data);
    } catch (JsonProcessingException ex) {
      log.warn("Failed to serialize summary data", ex);
      summary = "{}";
    }
    return "Resumen de la accion (" + intent + "): " + summary + ". " + CONFIRMATION_TEXT;
  }

  private String buildMissingFieldQuestion(String intent, String missingField) {
    switch (missingField) {
      case "nombreTrabajo":
        return "Para crear la cotizacion necesito el nombre del trabajo. ¿Cuál es?";
      case "clienteId|clienteManual.nombre":
        return "Necesito el cliente. ¿Tenés clienteId o querés cargar un cliente manual?";
      case "sucursalId":
        return "Necesito la sucursalId para la cotizacion. ¿Cuál es?";
      case "ubicacion.direccion":
        return "Necesito la direccion de la ubicacion. ¿Cuál es?";
      case "cotizacionId":
        return "Necesito el ID de la cotizacion. ¿Cuál es?";
      case "al_menos_un_campo_de_actualizacion":
        return "Necesito al menos un campo valido para actualizar. ¿Qué querés modificar?";
      default:
        return "Necesito el campo '" + missingField + "'. ¿Podés indicarlo?";
    }
  }

  private String buildErrorResponse(String message) {
    AssistantIntentResult result = new AssistantIntentResult();
    result.setIntent("CREATE_QUOTE");
    result.setData(new HashMap<>());
    result.setMissing_fields(List.of("nombreTrabajo", "clienteId|clienteManual.nombre", "sucursalId", "ubicacion.direccion"));
    result.setConfirmation_required(false);
    result.setMessage(message);
    try {
      return objectMapper.writeValueAsString(result);
    } catch (JsonProcessingException ex) {
      return "{\"intent\":\"CREATE_QUOTE\",\"data\":{},\"missing_fields\":[\"nombreTrabajo\",\"clienteId|clienteManual.nombre\",\"sucursalId\",\"ubicacion.direccion\"],\"confirmation_required\":false,\"message\":\""
          + message.replace("\"", "\\\"") + "\"}";
    }
  }

  private static class IntentDefinition {
    private final List<String> requiredFields;
    private final Set<String> neverInferFields;
    private final boolean confirmationRequired;

    IntentDefinition(List<String> requiredFields, Set<String> neverInferFields, boolean confirmationRequired) {
      this.requiredFields = requiredFields;
      this.neverInferFields = neverInferFields;
      this.confirmationRequired = confirmationRequired;
    }
  }
}
