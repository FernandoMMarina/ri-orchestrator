package com.ri.orchestrator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ri.orchestrator.dto.OllamaResponse;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AssistantService {
  private static final Logger log = LoggerFactory.getLogger(AssistantService.class);


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
      rawResponse = ollamaClient.generate(prompt);
    } catch (IllegalStateException ex) {
      log.error("Ollama call failed", ex);
      return "No se pudo contactar a la IA local.";
    }

    if (rawResponse == null || rawResponse.isBlank()) {
      return "No se recibio respuesta de la IA.";
    }

    OllamaResponse aiResponse;
    try {
      aiResponse = objectMapper.readValue(rawResponse, OllamaResponse.class);
    } catch (JsonProcessingException ex) {
      log.error("Invalid JSON from Ollama: {}", rawResponse, ex);
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
    return message;
  }

  private String safeMessage(String candidate, String fallback) {
    if (candidate == null || candidate.isBlank()) {
      return fallback;
    }
    return candidate;
  }
}
