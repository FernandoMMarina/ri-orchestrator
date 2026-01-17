package com.ri.orchestrator.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class IntentService {
    private static final Logger log = LoggerFactory.getLogger(IntentService.class);
    private final OllamaClient ollamaClient;

    public IntentService(OllamaClient ollamaClient) {
        this.ollamaClient = ollamaClient;
    }

    public String classifyClientType(String message) {
        String prompt = """
                Analiza el siguiente mensaje del usuario y clasifica su intención respecto al tipo de cliente.

                Categorías posibles:
                - EXISTENTE: El usuario quiere usar un cliente que ya existe en la base de datos (ej: "es uno que ya tenemos", "buscar cliente", "existente").
                - MANUAL: El usuario quiere cargar un cliente nuevo o manual (ej: "es nuevo", "no lo tengo", "manual", "particular", "consumidor final").
                - DESCONOCIDO: No queda claro qué quiere el usuario.

                Mensaje: "%s"

                Responde ÚNICAMENTE con una de las palabras clave: EXISTENTE, MANUAL, o DESCONOCIDO.
                """
                .formatted(message);

        try {
            String response = ollamaClient.generate(prompt);
            if (response == null)
                return "DESCONOCIDO";

            String normalized = response.trim().toUpperCase();
            if (normalized.contains("EXISTENTE"))
                return "EXISTENTE";
            if (normalized.contains("MANUAL"))
                return "MANUAL";

            return "DESCONOCIDO";
        } catch (Exception e) {
            log.error("Error classifying intent with AI", e);
            return "DESCONOCIDO";
        }
    }

    public boolean isAffirmative(String message) {
        String prompt = """
                Analiza si el siguiente mensaje es una respuesta AFIRMATIVA (sí, dale, ok, confirmo, etc.) o NEGATIVA (no, nop, nada, etc.).

                Mensaje: "%s"

                Responde ÚNICAMENTE: AFIRMATIVO o NEGATIVO.
                """
                .formatted(message);

        try {
            String response = ollamaClient.generate(prompt);
            if (response == null)
                return false;
            return response.trim().toUpperCase().contains("AFIRMATIVO");
        } catch (Exception e) {
            log.error("Error classifying affirmative with AI", e);
            return false;
        }
    }

    public String normalizeWorkType(String message, java.util.Collection<String> validOptions) {
        String optionsList = String.join(", ", validOptions);
        String prompt = """
                El usuario dijo: "%s"

                Opciones válidas: %s

                ¿A cuál opción se refiere? Responde ÚNICAMENTE con el nombre EXACTO de la opción (copia y pega), o "DESCONOCIDO" si no coincide con ninguna.
                """
                .formatted(message, optionsList);

        try {
            String response = ollamaClient.generate(prompt);
            if (response == null)
                return null;

            String cleaned = response.trim();
            // Verificar si la respuesta está en las opciones válidas
            for (String option : validOptions) {
                if (option.equalsIgnoreCase(cleaned)) {
                    return option;
                }
            }
            return null;
        } catch (Exception e) {
            log.error("Error normalizing work type with AI", e);
            return null;
        }
    }
}
