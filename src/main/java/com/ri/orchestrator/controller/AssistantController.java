package com.ri.orchestrator.controller;

import com.ri.orchestrator.dto.AssistantRequest;
import com.ri.orchestrator.dto.AssistantResponse;
import com.ri.orchestrator.service.AssistantService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class AssistantController {
  private final AssistantService assistantService;

  public AssistantController(AssistantService assistantService) {
    this.assistantService = assistantService;
  }

  @PostMapping(path = "/assistant", consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<AssistantResponse> assistant(@Valid @RequestBody AssistantRequest request) {
    AssistantResponse response = assistantService.handleMessage(request.getSessionId(), request.getMessage());
    return ResponseEntity.ok(response);
  }

  @GetMapping(path = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, String> health() {
    return Map.of("status", "UP");
  }
}
