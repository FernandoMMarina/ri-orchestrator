package com.ri.orchestrator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ri.orchestrator.dto.OllamaResponse;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class AssistantService {
  private static final String SYSTEM_PROMPT = "Sos un asistente interno de Rosenstein Instalaciones (RI). "
      + "Tu funcion NO es ejecutar acciones directamente, sino INTERPRETAR pedidos y devolver "
      + "instrucciones estructuradas para que el backend las ejecute.\n\n"
      + "REGLAS OBLIGATORIAS:\n"
      + "- Responde SIEMPRE en JSON valido.\n"
      + "- NO inventes datos.\n"
      + "- NO asumas valores que no esten explicitos.\n"
      + "- Si faltan datos, indica exactamente cuales faltan.\n"
      + "- Elegi SOLO una accion por respuesta.\n"
      + "- Si el pedido no corresponde a una accion, responde con action = \"ANSWER_ONLY\".\n\n"
      + "ACCIONES DISPONIBLES:\n"
      + "- CREATE_QUOTE\n"
      + "- GET_QUOTES\n"
      + "- GET_JOBS\n"
      + "- GET_JOB_STATUS\n"
      + "- CREATE_JOB\n"
      + "- HELP\n"
      + "- ANSWER_ONLY\n\n"
      + "FORMATO DE RESPUESTA OBLIGATORIO:\n"
      + "{\n"
      + "  \"action\": \"ACTION_NAME\",\n"
      + "  \"data\": { ... },\n"
      + "  \"missing_fields\": [],\n"
      + "  \"message\": \"Texto breve para mostrar al administrador\"\n"
      + "}\n\n"
      + "DESCRIPCION DE ACCIONES:\n"
      + "CREATE_QUOTE: data puede incluir cliente, sucursal, maquina, descripcion, fecha.\n"
      + "GET_QUOTES: data puede incluir estado, cliente, fecha_desde, fecha_hasta.\n"
      + "GET_JOBS: data puede incluir estado, tecnico, fecha.\n"
      + "GET_JOB_STATUS: data debe incluir job_id.\n"
      + "CREATE_JOB: data puede incluir cliente, sucursal, maquina, descripcion, fecha.\n"
      + "HELP: usar cuando el usuario pide ayuda o no sabe que puede hacer.\n"
      + "ANSWER_ONLY: usar solo si el pedido es informativo o conversacional y no requiere backend.\n\n"
      + "Responde solo con JSON y sin texto adicional.";

  private final OllamaClient ollamaClient;
  private final AwsBackendClient awsBackendClient;
  private final JobsServiceClient jobsServiceClient;
  private final ObjectMapper objectMapper;

  public AssistantService(OllamaClient ollamaClient,
                          AwsBackendClient awsBackendClient,
                          JobsServiceClient jobsServiceClient,
                          ObjectMapper objectMapper) {
    this.ollamaClient = ollamaClient;
    this.awsBackendClient = awsBackendClient;
    this.jobsServiceClient = jobsServiceClient;
    this.objectMapper = objectMapper;
  }

  public String handleMessage(String message) {
    String prompt = buildPrompt(message);
    String rawResponse;
    try {
      rawResponse = ollamaClient.generateResponse(prompt);
    } catch (IllegalStateException ex) {
      return "No se pudo contactar a la IA local.";
    }

    if (rawResponse == null || rawResponse.isBlank()) {
      return "No se recibio respuesta de la IA.";
    }

    OllamaResponse aiResponse;
    try {
      aiResponse = objectMapper.readValue(rawResponse, OllamaResponse.class);
    } catch (JsonProcessingException ex) {
      return "La IA devolvio una respuesta invalida.";
    }

    if (aiResponse.getAction() == null) {
      return "La IA no indico una accion.";
    }

    List<String> missing = aiResponse.getMissing_fields();
    if (missing != null && !missing.isEmpty()) {
      return safeMessage(aiResponse.getMessage(), "Faltan datos para continuar.");
    }

    String action = aiResponse.getAction().toUpperCase(Locale.ROOT);
    try {
      switch (action) {
        case "CREATE_QUOTE":
          return awsBackendClient.createQuote(aiResponse.getData());
        case "GET_JOBS":
          return jobsServiceClient.getJobs(aiResponse.getData());
        case "ANSWER_ONLY":
          return safeMessage(aiResponse.getMessage(), "Listo.");
        case "HELP":
          return "Puedo ayudarte a consultar trabajos, crear cotizaciones y resumir informacion.";
        default:
          return "Accion no reconocida por el orquestador.";
      }
    } catch (IllegalStateException ex) {
      return "No se pudo completar la solicitud contra servicios internos.";
    }
  }

  private String buildPrompt(String message) {
    return SYSTEM_PROMPT + "\n\nUsuario: \"" + message + "\"";
  }

  private String safeMessage(String candidate, String fallback) {
    if (candidate == null || candidate.isBlank()) {
      return fallback;
    }
    return candidate;
  }
}
