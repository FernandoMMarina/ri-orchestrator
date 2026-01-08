package com.ri.orchestrator.service;

import com.ri.orchestrator.dto.AssistantResponse;
import com.ri.orchestrator.model.ConversationSession;
import com.ri.orchestrator.model.ConversationState;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AssistantService {
  private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

  private static final String CONTEXT_CLIENTE_TIPO = "tipoCliente";
  private static final String CONTEXT_CLIENTE_ID = "clienteId";
  private static final String CONTEXT_CLIENTE_NOMBRE = "clienteNombre";
  private static final String CONTEXT_CLIENTE_MANUAL = "clienteManual";
  private static final String CONTEXT_CLIENTE_MATCHES = "clienteMatches";
  private static final String CONTEXT_SUCURSAL_ID = "sucursalId";
  private static final String CONTEXT_UBICACION_DIRECCION = "ubicacionDireccion";
  private static final String CONTEXT_NOMBRE_TRABAJO = "nombreTrabajo";
  private static final String CONTEXT_MANO_OBRA = "manoDeObra";
  private static final String CONTEXT_MATERIALES = "materiales";
  private static final String CONTEXT_EQUIPOS = "equipos";
  private static final String CONTEXT_EXTRAS = "extras";
  private static final String CONTEXT_MO_CONFIRM_ZERO = "manoDeObraZeroPending";
  private static final String CONTEXT_APROBADO = "aprobado";
  private static final String CONTEXT_ESTADO = "estado";

  private static final Pattern OBJECT_ID_PATTERN = Pattern.compile("([a-fA-F0-9]{24})");
  private static final Pattern NUMBER_PATTERN = Pattern.compile("([0-9]+([\\.,][0-9]+)?)");
  private static final Pattern INTEGER_PATTERN = Pattern.compile("^(\\d+)$");

  private static final Map<String, String> TRABAJO_CATALOGO = buildTrabajoCatalog();
  private static final Set<String> FINISH_KEYWORDS = Set.of(
      "terminar", "terminamos", "finalizar", "cerrar", "listo", "resumen"
  );

  private final OllamaClient ollamaClient;
  private final SessionStore sessionStore;
  private final AwsBackendClient awsBackendClient;

  public AssistantService(OllamaClient ollamaClient,
                          SessionStore sessionStore,
                          AwsBackendClient awsBackendClient) {
    this.ollamaClient = ollamaClient;
    this.sessionStore = sessionStore;
    this.awsBackendClient = awsBackendClient;
  }

  public AssistantResponse handleMessage(String sessionId, String message) {
    String resolvedSessionId = resolveSessionId(sessionId);
    ConversationSession session = sessionStore.getOrCreate(resolvedSessionId);
    String replyText = "";
    boolean endSession = false;

    try {
      switch (session.getState()) {
        case START:
          changeState(session, ConversationState.CAPTURA_TIPO_CLIENTE);
          replyText = buildAskTipoCliente();
          break;
        case CAPTURA_TIPO_CLIENTE:
          ClientType clientType = classifyClientType(message);
          if (clientType == ClientType.UNKNOWN) {
            replyText = buildAskTipoCliente();
          } else if (clientType == ClientType.EXISTENTE) {
            session.getContext().put(CONTEXT_CLIENTE_TIPO, "existente");
            changeState(session, ConversationState.CAPTURA_CLIENTE_EXISTENTE_NOMBRE);
            replyText = buildAskClienteExistenteNombre();
          } else {
            session.getContext().put(CONTEXT_CLIENTE_TIPO, "manual");
            changeState(session, ConversationState.CAPTURA_CLIENTE_MANUAL);
            replyText = buildAskClienteManual();
          }
          break;
        case CAPTURA_CLIENTE_EXISTENTE:
        case CAPTURA_CLIENTE_EXISTENTE_NOMBRE:
          String clienteNombre = sanitizeText(message);
          if (clienteNombre.isBlank()) {
            replyText = buildAskClienteExistenteInvalid();
          } else {
            List<Map<String, Object>> matches = awsBackendClient.searchUsersByName(clienteNombre);
            if (matches.isEmpty()) {
              replyText = buildAskClienteExistenteNotFound();
            } else if (matches.size() == 1) {
              Map<String, Object> cliente = matches.get(0);
              String clienteId = resolveClienteId(cliente);
              if (clienteId == null) {
                replyText = buildAskClienteExistenteResolutionError();
              } else {
                session.getContext().put(CONTEXT_CLIENTE_ID, clienteId);
                session.getContext().put(CONTEXT_CLIENTE_NOMBRE, resolveClienteDisplayName(cliente));
                changeState(session, ConversationState.CAPTURA_SUCURSAL);
                replyText = buildAskSucursal();
              }
            } else {
              session.getContext().put(CONTEXT_CLIENTE_MATCHES, new ArrayList<>(matches));
              changeState(session, ConversationState.CAPTURA_CLIENTE_EXISTENTE_CONFIRMACION);
              replyText = buildAskClienteExistenteMultiple(matches);
            }
          }
          break;
        case CAPTURA_CLIENTE_EXISTENTE_CONFIRMACION:
          Object matchesObj = session.getContext().get(CONTEXT_CLIENTE_MATCHES);
          if (!(matchesObj instanceof List<?> matchesRaw)) {
            changeState(session, ConversationState.CAPTURA_CLIENTE_EXISTENTE_NOMBRE);
            replyText = buildAskClienteExistenteNombre();
            break;
          }
          List<Map<String, Object>> matches = new ArrayList<>();
          for (Object item : matchesRaw) {
            if (item instanceof Map<?, ?> mapItem) {
              Map<String, Object> casted = new HashMap<>();
              mapItem.forEach((key, value) -> {
                if (key instanceof String) {
                  casted.put((String) key, value);
                }
              });
              matches.add(casted);
            }
          }
          if (matches.isEmpty()) {
            session.getContext().remove(CONTEXT_CLIENTE_MATCHES);
            changeState(session, ConversationState.CAPTURA_CLIENTE_EXISTENTE_NOMBRE);
            replyText = buildAskClienteExistenteNombre();
            break;
          }
          int selection = parseSelectionIndex(message);
          if (selection < 1 || selection > matches.size()) {
            replyText = buildAskClienteExistenteConfirmationInvalid(matches.size());
          } else {
            Map<String, Object> cliente = matches.get(selection - 1);
            String clienteId = resolveClienteId(cliente);
            if (clienteId == null) {
              session.getContext().remove(CONTEXT_CLIENTE_MATCHES);
              changeState(session, ConversationState.CAPTURA_CLIENTE_EXISTENTE_NOMBRE);
              replyText = buildAskClienteExistenteResolutionError();
            } else {
              session.getContext().put(CONTEXT_CLIENTE_ID, clienteId);
              session.getContext().put(CONTEXT_CLIENTE_NOMBRE, resolveClienteDisplayName(cliente));
              session.getContext().remove(CONTEXT_CLIENTE_MATCHES);
              changeState(session, ConversationState.CAPTURA_SUCURSAL);
              replyText = buildAskSucursal();
            }
          }
          break;
        case CAPTURA_CLIENTE_MANUAL:
          String clienteManualNombre = sanitizeText(message);
          if (clienteManualNombre.isBlank()) {
            replyText = buildAskClienteManual();
          } else {
            Map<String, Object> clienteManual = new HashMap<>();
            clienteManual.put("nombre", clienteManualNombre);
            session.getContext().put(CONTEXT_CLIENTE_MANUAL, clienteManual);
            changeState(session, ConversationState.CAPTURA_DIRECCION_MANUAL);
            replyText = buildAskDireccionManual();
          }
          break;
        case CAPTURA_SUCURSAL:
          String sucursalId = parseObjectId(message);
          if (sucursalId == null) {
            replyText = buildAskSucursalInvalid();
          } else {
            session.getContext().put(CONTEXT_SUCURSAL_ID, sucursalId);
            changeState(session, ConversationState.CAPTURA_TRABAJO);
            replyText = buildAskTrabajo();
          }
          break;
        case CAPTURA_DIRECCION_MANUAL:
          String direccionManual = sanitizeText(message);
          if (direccionManual.isBlank()) {
            replyText = buildAskDireccionManual();
          } else {
            session.getContext().put(CONTEXT_UBICACION_DIRECCION, direccionManual);
            changeState(session, ConversationState.CAPTURA_TRABAJO);
            replyText = buildAskTrabajo();
          }
          break;
        case CAPTURA_TRABAJO:
          String trabajo = resolveTrabajo(message);
          if (trabajo == null) {
            replyText = buildAskTrabajoInvalid();
          } else {
            session.getContext().put(CONTEXT_NOMBRE_TRABAJO, trabajo);
            changeState(session, ConversationState.CAPTURA_MANO_OBRA);
            replyText = buildAskManoObra();
          }
          break;
        case CAPTURA_MANO_OBRA:
          if (Boolean.TRUE.equals(session.getContext().get(CONTEXT_MO_CONFIRM_ZERO))) {
            if (isYes(message)) {
              session.getContext().remove(CONTEXT_MO_CONFIRM_ZERO);
              changeState(session, ConversationState.CAPTURA_MATERIALES_CONFIRM);
              replyText = buildAskMaterialesConfirm();
            } else if (isNo(message)) {
              session.getContext().remove(CONTEXT_MO_CONFIRM_ZERO);
              replyText = buildAskManoObra();
            } else {
              replyText = buildConfirmManoObraZero();
            }
            break;
          }

          Double manoDeObra = parseAmount(message);
          if (manoDeObra == null || manoDeObra < 0) {
            replyText = buildAskManoObraInvalid();
          } else {
            session.getContext().put(CONTEXT_MANO_OBRA, manoDeObra);
            if (manoDeObra == 0) {
              session.getContext().put(CONTEXT_MO_CONFIRM_ZERO, true);
              replyText = buildConfirmManoObraZero();
            } else {
              changeState(session, ConversationState.CAPTURA_MATERIALES_CONFIRM);
              replyText = buildAskMaterialesConfirm();
            }
          }
          break;
        case CAPTURA_MATERIALES_CONFIRM:
          if (isYes(message)) {
            ensureList(session.getContext(), CONTEXT_MATERIALES);
            changeState(session, ConversationState.CAPTURA_MATERIALES);
            replyText = buildAskMaterialesItem();
          } else if (isNo(message)) {
            changeState(session, ConversationState.CAPTURA_EQUIPOS_CONFIRM);
            replyText = buildAskEquiposConfirm();
          } else {
            replyText = buildAskMaterialesConfirm();
          }
          break;
        case CAPTURA_MATERIALES:
          replyText = handleAdditionalItems(
              session,
              message,
              CONTEXT_MATERIALES,
              ConversationState.CAPTURA_EQUIPOS_CONFIRM,
              this::buildAskEquiposConfirm,
              this::buildAskMaterialesItem,
              this::buildAskMaterialesItemInvalid,
              this::buildAskMaterialesMore
          );
          break;
        case CAPTURA_EQUIPOS_CONFIRM:
          if (isYes(message)) {
            ensureList(session.getContext(), CONTEXT_EQUIPOS);
            changeState(session, ConversationState.CAPTURA_EQUIPOS);
            replyText = buildAskEquiposItem();
          } else if (isNo(message)) {
            changeState(session, ConversationState.CAPTURA_EXTRAS_CONFIRM);
            replyText = buildAskExtrasConfirm();
          } else {
            replyText = buildAskEquiposConfirm();
          }
          break;
        case CAPTURA_EQUIPOS:
          replyText = handleAdditionalItems(
              session,
              message,
              CONTEXT_EQUIPOS,
              ConversationState.CAPTURA_EXTRAS_CONFIRM,
              this::buildAskExtrasConfirm,
              this::buildAskEquiposItem,
              this::buildAskEquiposItemInvalid,
              this::buildAskEquiposMore
          );
          break;
        case CAPTURA_EXTRAS_CONFIRM:
          if (isYes(message)) {
            ensureList(session.getContext(), CONTEXT_EXTRAS);
            changeState(session, ConversationState.CAPTURA_EXTRAS);
            replyText = buildAskExtrasItem();
          } else if (isNo(message)) {
            replyText = buildSummaryAndMoveToConfirmation(session);
          } else {
            replyText = buildAskExtrasConfirm();
          }
          break;
        case CAPTURA_EXTRAS:
          if (isFinish(message)) {
            if (hasItems(session.getContext(), CONTEXT_EXTRAS)) {
              replyText = buildSummaryAndMoveToConfirmation(session);
            } else {
              replyText = buildAskExtrasItem();
            }
          } else {
            ParsedItem item = parseDescriptionAndAmount(message);
            if (item == null) {
              replyText = buildAskExtrasItemInvalid();
            } else {
              addAdditionalItem(session.getContext(), CONTEXT_EXTRAS, item);
              replyText = buildAskExtrasMore();
            }
          }
          break;
        case RESUMEN:
          replyText = buildSummaryAndMoveToConfirmation(session);
          break;
        case CONFIRMACION:
          if (isConfirmed(message) || isSimpleYes(message)) {
            changeState(session, ConversationState.SUCCESS);
            replyText = buildSuccess();
            endSession = true;
          } else {
            replyText = buildConfirmationPrompt();
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

    AssistantResponse response = new AssistantResponse(
        session.getSessionId(),
        session.getState().name(),
        replyText,
        endSession
    );
    if (session.getState() == ConversationState.CONFIRMACION) {
      response.setAwaiting_confirmation(true);
      response.setNext_action("await_confirmation");
    }
    return response;
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

 private ClientType classifyClientType(String message) {
  String normalized = normalize(message);

  // 1️⃣ MANUAL tiene prioridad absoluta
  if (normalized.contains("manual")
      || normalized.contains("nuevo")
      || normalized.contains("particular")
      || normalized.contains("sin cliente")) {
    return ClientType.MANUAL;
  }

  // 2️⃣ EXISTENTE solo si lo dice explícitamente
  if (normalized.contains("existente")) {
    return ClientType.EXISTENTE;
  }

  // 3️⃣ EXISTENTE si parece un ObjectId
  if (parseObjectId(normalized) != null) {
    return ClientType.EXISTENTE;
  }

  return ClientType.UNKNOWN;
}

  private String parseObjectId(String message) {
    if (message == null) {
      return null;
    }
    Matcher matcher = OBJECT_ID_PATTERN.matcher(message.trim());
    if (matcher.find()) {
      return matcher.group(1);
    }
    return null;
  }

  private String resolveClienteId(Map<String, Object> cliente) {
    if (cliente == null) {
      return null;
    }
    Object id = cliente.get("_id");
    if (id == null) {
      id = cliente.get("id");
    }
    if (id == null) {
      return null;
    }
    String idText = String.valueOf(id).trim();
    return idText.isBlank() ? null : idText;
  }

  private String resolveClienteDisplayName(Map<String, Object> cliente) {
    if (cliente == null) {
      return "Cliente sin nombre";
    }
    Object nombre = cliente.get("nombre");
    if (nombre == null) {
      nombre = cliente.get("name");
    }
    if (nombre == null) {
      nombre = cliente.get("razonSocial");
    }
    if (nombre == null) {
      return "Cliente sin nombre";
    }
    String display = String.valueOf(nombre).trim();
    return display.isBlank() ? "Cliente sin nombre" : display;
  }

  private int parseSelectionIndex(String message) {
    if (message == null) {
      return -1;
    }
    String trimmed = message.trim();
    Matcher matcher = INTEGER_PATTERN.matcher(trimmed);
    if (!matcher.matches()) {
      return -1;
    }
    try {
      return Integer.parseInt(matcher.group(1));
    } catch (NumberFormatException ex) {
      return -1;
    }
  }

  private String resolveTrabajo(String message) {
    String normalized = normalize(message);
    if (normalized.isBlank()) {
      return null;
    }
    return TRABAJO_CATALOGO.get(normalized);
  }

  private Double parseAmount(String message) {
    if (message == null) {
      return null;
    }
    Matcher matcher = NUMBER_PATTERN.matcher(message.replace(" ", ""));
    if (!matcher.find()) {
      return null;
    }
    String raw = matcher.group(1);
    raw = raw.replace(",", ".");
    try {
      return Double.parseDouble(raw);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private ParsedItem parseDescriptionAndAmount(String message) {
    if (message == null) {
      return null;
    }
    Matcher matcher = NUMBER_PATTERN.matcher(message);
    String lastMatch = null;
    while (matcher.find()) {
      lastMatch = matcher.group(1);
    }
    if (lastMatch == null) {
      return null;
    }
    Double amount = parseAmount(lastMatch);
    if (amount == null || amount < 0) {
      return null;
    }
    String description = message.replace(lastMatch, "").trim();
    if (description.isBlank()) {
      return null;
    }
    return new ParsedItem(description, amount);
  }

  private String handleAdditionalItems(ConversationSession session,
                                       String message,
                                       String contextKey,
                                       ConversationState nextState,
                                       ResponseSupplier nextPrompt,
                                       ResponseSupplier askPrompt,
                                       ResponseSupplier invalidPrompt,
                                       ResponseSupplier morePrompt) {
    if (isFinish(message)) {
      if (hasItems(session.getContext(), contextKey)) {
        changeState(session, nextState);
        return nextPrompt.get();
      }
      return askPrompt.get();
    }
    ParsedItem item = parseDescriptionAndAmount(message);
    if (item == null) {
      return invalidPrompt.get();
    }
    addAdditionalItem(session.getContext(), contextKey, item);
    return morePrompt.get();
  }

  private boolean isFinish(String message) {
    String normalized = normalize(message);
    if (normalized.isBlank()) {
      return false;
    }
    for (String keyword : FINISH_KEYWORDS) {
      if (normalized.contains(keyword)) {
        return true;
      }
    }
    return false;
  }

  private void addAdditionalItem(Map<String, Object> context, String key, ParsedItem item) {
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items = (List<Map<String, Object>>) context.computeIfAbsent(
        key, k -> new ArrayList<Map<String, Object>>());
    Map<String, Object> entry = new HashMap<>();
    entry.put("descripcion", item.descripcion);
    entry.put("monto", item.monto);
    items.add(entry);
  }

  private void ensureList(Map<String, Object> context, String key) {
    context.computeIfAbsent(key, k -> new ArrayList<Map<String, Object>>());
  }

  private boolean hasItems(Map<String, Object> context, String key) {
    Object items = context.get(key);
    if (!(items instanceof List<?>)) {
      return false;
    }
    return !((List<?>) items).isEmpty();
  }

  private boolean isYes(String message) {
    String normalized = normalize(message);
    return normalized.equals("si")
        || normalized.equals("sí")
        || normalized.startsWith("si ")
        || normalized.startsWith("sí ")
        || normalized.contains("claro")
        || normalized.contains("ok")
        || normalized.contains("dale")
        || normalized.contains("tengo")
        || normalized.contains("hay");
  }

  private boolean isNo(String message) {
    String normalized = normalize(message);
    return normalized.equals("no")
        || normalized.startsWith("no ")
        || normalized.contains("no hay")
        || normalized.contains("ninguno")
        || normalized.contains("ninguna");
  }

  private boolean isConfirmed(String message) {
    String normalized = normalize(message);
    return normalized.contains("confirmo")
        || normalized.contains("confirmar");
  }

  private boolean isSimpleYes(String message) {
    String normalized = normalize(message);
    return normalized.equals("si")
        || normalized.equals("sí")
        || normalized.startsWith("si ")
        || normalized.startsWith("sí ");
  }

  private String normalize(String message) {
    if (message == null) {
      return "";
    }
    String lower = message.toLowerCase(Locale.ROOT).trim();
    String normalized = Normalizer.normalize(lower, Normalizer.Form.NFD);
    return normalized.replaceAll("\\p{M}", "");
  }

  private String sanitizeText(String message) {
    return message == null ? "" : message.trim();
  }

  private String buildSummaryAndMoveToConfirmation(ConversationSession session) {
    Map<String, Object> context = session.getContext();
    context.put(CONTEXT_APROBADO, false);
    context.put(CONTEXT_ESTADO, "pendiente");
    double totalCost = calculateTotalCost(context);
    double totalIva = totalCost * 1.21;
    context.put("totalCost", totalCost);
    context.put("totalIva", totalIva);

    String summaryPayload = buildSummaryPayload(context, totalCost, totalIva);
    String replyText = renderWithOllama(
        "Redacta un resumen breve con confirmacion explicita. Contexto: " + summaryPayload,
        buildSummaryFallback(context, totalCost, totalIva)
    );

    changeState(session, ConversationState.CONFIRMACION);
    return replyText;
  }

  private double calculateTotalCost(Map<String, Object> context) {
    double manoDeObra = asDouble(context.get(CONTEXT_MANO_OBRA));
    double materiales = sumArray(context.get(CONTEXT_MATERIALES));
    double equipos = sumArray(context.get(CONTEXT_EQUIPOS));
    double extras = sumArray(context.get(CONTEXT_EXTRAS));
    return manoDeObra + materiales + equipos + extras;
  }

  private double sumArray(Object value) {
    if (!(value instanceof List<?>)) {
      return 0;
    }
    double total = 0;
    for (Object entry : (List<?>) value) {
      if (entry instanceof Map<?, ?> mapEntry) {
        Object amount = mapEntry.get("monto");
        total += asDouble(amount);
      }
    }
    return total;
  }

  private double asDouble(Object value) {
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    if (value instanceof String text) {
      try {
        return Double.parseDouble(text);
      } catch (NumberFormatException ex) {
        return 0;
      }
    }
    return 0;
  }

  private String formatMoney(double value) {
    return String.format(Locale.ROOT, "%.2f", value);
  }

  private String buildSummaryPayload(Map<String, Object> context, double totalCost, double totalIva) {
    String cliente = resolveClienteLabel(context);
    String trabajo = String.valueOf(context.getOrDefault(CONTEXT_NOMBRE_TRABAJO, ""));
    String manoDeObra = formatMoney(asDouble(context.get(CONTEXT_MANO_OBRA)));
    String materiales = formatMoney(sumArray(context.get(CONTEXT_MATERIALES)));
    String equipos = formatMoney(sumArray(context.get(CONTEXT_EQUIPOS)));
    String extras = formatMoney(sumArray(context.get(CONTEXT_EXTRAS)));
    return "Cliente: " + cliente
        + ". Trabajo: " + trabajo
        + ". Mano de obra: " + manoDeObra
        + ". Materiales: " + materiales
        + ". Equipos: " + equipos
        + ". Extras: " + extras
        + ". Total sin IVA: " + formatMoney(totalCost)
        + ". Total con IVA: " + formatMoney(totalIva);
  }

  private String buildSummaryFallback(Map<String, Object> context, double totalCost, double totalIva) {
    return "Resumen de la cotización. " + buildSummaryPayload(context, totalCost, totalIva)
        + ". ¿Confirmás esta acción? Respondé: CONFIRMAR";
  }

  private String resolveClienteLabel(Map<String, Object> context) {
    Object clienteId = context.get(CONTEXT_CLIENTE_ID);
    if (clienteId != null) {
      return "ID " + clienteId;
    }
    Object manual = context.get(CONTEXT_CLIENTE_MANUAL);
    if (manual instanceof Map<?, ?> manualMap) {
      Object nombre = manualMap.get("nombre");
      if (nombre != null && !String.valueOf(nombre).isBlank()) {
        return String.valueOf(nombre);
      }
    }
    return "No especificado";
  }

  private String buildAskTipoCliente() {
    return renderWithOllama(
        "Redacta una pregunta breve para pedir si el cliente es existente o manual. Responde solo la pregunta.",
        "¿El cliente es existente o manual?"
    );
  }

  private String buildAskClienteExistenteNombre() {
    return renderWithOllama(
        "Redacta una pregunta breve para pedir el nombre del cliente existente. Responde solo la pregunta.",
        "Decime el nombre del cliente existente."
    );
  }

  private String buildAskClienteExistenteInvalid() {
    return "Necesito un nombre de cliente válido. ¿Cuál es?";
  }

  private String buildAskClienteExistenteNotFound() {
    return "No encontré un cliente con ese nombre. ¿Querés intentar con otro?";
  }

  private String buildAskClienteExistenteResolutionError() {
    return "No pude resolver ese cliente. ¿Podés intentar con otro nombre?";
  }

  private String buildAskClienteExistenteMultiple(List<Map<String, Object>> matches) {
    StringBuilder builder = new StringBuilder("Encontré varios clientes:\n");
    for (int i = 0; i < matches.size(); i++) {
      builder.append(i + 1)
          .append(") ")
          .append(resolveClienteDisplayName(matches.get(i)))
          .append("\n");
    }
    builder.append("¿Cuál es?");
    return builder.toString();
  }

  private String buildAskClienteExistenteConfirmationInvalid(int max) {
    return "Indicame un número válido del 1 al " + max + ".";
  }

  private String buildAskClienteManual() {
    return renderWithOllama(
        "Redacta una pregunta breve para pedir el nombre del cliente manual. Responde solo la pregunta.",
        "Indicame el nombre del cliente."
    );
  }

  private String buildAskSucursal() {
    return renderWithOllama(
        "Redacta una pregunta breve para pedir la sucursalId del cliente. Responde solo la pregunta.",
        "Pasame la sucursalId del cliente."
    );
  }

  private String buildAskSucursalInvalid() {
    return "Necesito una sucursalId válida (24 caracteres hex). ¿Cuál es?";
  }

  private String buildAskDireccionManual() {
    return renderWithOllama(
        "Redacta una pregunta breve para pedir la direccion de la ubicacion. Responde solo la pregunta.",
        "Indicame la dirección completa de la ubicación."
    );
  }

  private String buildAskTrabajo() {
    return renderWithOllama(
        "Redacta una pregunta breve para elegir el tipo de trabajo. Inclui las opciones.",
        "¿Cuál es el tipo de trabajo? Opciones: " + String.join(", ", TRABAJO_CATALOGO.values())
    );
  }

  private String buildAskTrabajoInvalid() {
    return "El nombre del trabajo no está en el catálogo. Opciones: "
        + String.join(", ", TRABAJO_CATALOGO.values());
  }

  private String buildAskManoObra() {
    return renderWithOllama(
        "Redacta una pregunta breve para pedir el monto de mano de obra. Responde solo la pregunta.",
        "Indicame el monto de mano de obra."
    );
  }

  private String buildAskManoObraInvalid() {
    return "Necesito un monto numérico (>= 0) para mano de obra. ¿Cuál es?";
  }

  private String buildConfirmManoObraZero() {
    return "La mano de obra es 0. ¿Confirmás continuar?";
  }

  private String buildAskMaterialesConfirm() {
    return renderWithOllama(
        "Redacta una pregunta breve para saber si hay materiales. Responde solo la pregunta.",
        "¿Hay materiales para agregar?"
    );
  }

  private String buildAskMaterialesItem() {
    return renderWithOllama(
        "Redacta una pregunta breve para pedir descripcion y monto de un material.",
        "Indicame la descripción y el monto del material."
    );
  }

  private String buildAskMaterialesItemInvalid() {
    return "Necesito descripción y monto numérico del material. Por ejemplo: 'Cable 2mm 1500'.";
  }

  private String buildAskMaterialesMore() {
    return renderWithOllama(
        "Redacta una pregunta breve para pedir si hay mas materiales o finalizar.",
        "¿Querés agregar otro material o terminamos?"
    );
  }

  private String buildAskEquiposConfirm() {
    return renderWithOllama(
        "Redacta una pregunta breve para saber si hay equipos. Responde solo la pregunta.",
        "¿Hay equipos para agregar?"
    );
  }

  private String buildAskEquiposItem() {
    return renderWithOllama(
        "Redacta una pregunta breve para pedir descripcion y monto de un equipo.",
        "Indicame la descripción y el monto del equipo."
    );
  }

  private String buildAskEquiposItemInvalid() {
    return "Necesito descripción y monto numérico del equipo. Por ejemplo: 'Compresor 5000'.";
  }

  private String buildAskEquiposMore() {
    return renderWithOllama(
        "Redacta una pregunta breve para pedir si hay mas equipos o finalizar.",
        "¿Querés agregar otro equipo o terminamos?"
    );
  }

  private String buildAskExtrasConfirm() {
    return renderWithOllama(
        "Redacta una pregunta breve para saber si hay extras. Responde solo la pregunta.",
        "¿Hay extras para agregar?"
    );
  }

  private String buildAskExtrasItem() {
    return renderWithOllama(
        "Redacta una pregunta breve para pedir descripcion y monto de un extra.",
        "Indicame la descripción y el monto del extra."
    );
  }

  private String buildAskExtrasItemInvalid() {
    return "Necesito descripción y monto numérico del extra. Por ejemplo: 'Viáticos 2000'.";
  }

  private String buildAskExtrasMore() {
    return renderWithOllama(
        "Redacta una pregunta breve para pedir si hay mas extras o finalizar.",
        "¿Querés agregar otro extra o terminamos?"
    );
  }

  private String buildConfirmationPrompt() {
    return "¿Confirmás esta acción? Respondé: CONFIRMAR";
  }

  private String buildSuccess() {
    return renderWithOllama(
        "Redacta una confirmacion breve de que la cotizacion quedó creada.",
        "Listo, la cotización quedó creada."
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

  private static Map<String, String> buildTrabajoCatalog() {
    Map<String, String> catalog = new HashMap<>();
    List<String> options = List.of(
        "Instalación A/A",
        "Mantenimiento A/A",
        "Reparación A/A",
        "Instalación Caldera",
        "Mantenimiento Caldera",
        "Reparación Caldera",
        "Consultoría",
        "Plomeria",
        "Mecanica",
        "Herreria",
        "Electricidad",
        "Automatizaciones",
        "Autoelevadores",
        "Cabina de Pintura",
        "Neumatica",
        "Obra",
        "Chargebox"
    );
    for (String option : options) {
      String normalized = normalizeStatic(option);
      catalog.put(normalized, option);
    }
    return catalog;
  }

  private static String normalizeStatic(String message) {
    if (message == null) {
      return "";
    }
    String lower = message.toLowerCase(Locale.ROOT).trim();
    String normalized = Normalizer.normalize(lower, Normalizer.Form.NFD);
    return normalized.replaceAll("\\p{M}", "");
  }

  private enum ClientType {
    EXISTENTE,
    MANUAL,
    UNKNOWN
  }

  private record ParsedItem(String descripcion, double monto) {}

  private interface ResponseSupplier {
    String get();
  }
}
