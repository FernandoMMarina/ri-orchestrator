package com.ri.orchestrator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ri.orchestrator.dto.ParsedFinancialItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SmartParserService {
    private static final Logger log = LoggerFactory.getLogger(SmartParserService.class);
    private final OllamaClient ollamaClient;
    private final ObjectMapper objectMapper;

    public SmartParserService(OllamaClient ollamaClient, ObjectMapper objectMapper) {
        this.ollamaClient = ollamaClient;
        this.objectMapper = objectMapper;
    }

    public ParsedFinancialItem parseFinancialItem(String message) {
        String prompt = """
                Analiza el siguiente texto y extrae la descripción del ítem y el monto TOTAL expresado en dinero.
                Si hay cálculos matemáticos implícitos (ej: "2 unidades de 500"), calculá el total (1000).
                Si no hay monto explícito, asumí 0.

                Texto: "%s"

                Responde ÚNICAMENTE con un JSON válido con este formato:
                {
                  "description": "Texto descriptivo limpio",
                  "amount": 123.45
                }
                """.formatted(message);

        try {
            String response = ollamaClient.generate(prompt);
            if (response == null)
                return null;

            // Extract JSON logic in case Ollama wraps it in markdown code blocks
            String json = extractJson(response);
            JsonNode root = objectMapper.readTree(json);

            String description = root.path("description").asText("Item desconocido");
            double amount = root.path("amount").asDouble(0.0);

            return new ParsedFinancialItem(description, amount);
        } catch (Exception e) {
            log.error("Error parsing financial item with AI", e);
            return null;
        }
    }

    private String extractJson(String text) {
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }
}
