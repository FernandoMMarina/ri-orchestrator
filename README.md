# ri-orchestrator smoke tests

These examples assume the service is running locally on port 8080.
They use `jq` to print the `state` field.

## Happy path: existing client + sucursal + material + confirm

```bash
BASE_URL="http://localhost:8080"
RESP=$(curl -s -X POST "$BASE_URL/assistant" \
  -H "Content-Type: application/json" \
  -d '{"message":"cliente existente"}')
SESSION_ID=$(echo "$RESP" | jq -r '.session_id')
echo "$RESP" | jq '.state'

# Expected state: CAPTURA_CLIENTE_EXISTENTE
curl -s -X POST "$BASE_URL/assistant" \
  -H "Content-Type: application/json" \
  -d "{\"sessionId\":\"$SESSION_ID\",\"message\":\"5f1d7f3e8b1c2a3d4e5f6789\"}" | jq '.state'

# Expected state: CAPTURA_SUCURSAL
curl -s -X POST "$BASE_URL/assistant" \
  -H "Content-Type: application/json" \
  -d "{\"sessionId\":\"$SESSION_ID\",\"message\":\"5f1d7f3e8b1c2a3d4e5f6790\"}" | jq '.state'

# Expected state: CAPTURA_TRABAJO
curl -s -X POST "$BASE_URL/assistant" \
  -H "Content-Type: application/json" \
  -d "{\"sessionId\":\"$SESSION_ID\",\"message\":\"Instalación A/A\"}" | jq '.state'

# Expected state: CAPTURA_MANO_OBRA
curl -s -X POST "$BASE_URL/assistant" \
  -H "Content-Type: application/json" \
  -d "{\"sessionId\":\"$SESSION_ID\",\"message\":\"25000\"}" | jq '.state'

# Expected state: CAPTURA_MATERIALES_CONFIRM
curl -s -X POST "$BASE_URL/assistant" \
  -H "Content-Type: application/json" \
  -d "{\"sessionId\":\"$SESSION_ID\",\"message\":\"si\"}" | jq '.state'

# Expected state: CAPTURA_MATERIALES
curl -s -X POST "$BASE_URL/assistant" \
  -H "Content-Type: application/json" \
  -d "{\"sessionId\":\"$SESSION_ID\",\"message\":\"Cable 2mm 1500\"}" | jq '.state'

# Expected state: CAPTURA_EQUIPOS_CONFIRM
curl -s -X POST "$BASE_URL/assistant" \
  -H "Content-Type: application/json" \
  -d "{\"sessionId\":\"$SESSION_ID\",\"message\":\"terminar\"}" | jq '.state'

# Expected state: CAPTURA_EXTRAS_CONFIRM
curl -s -X POST "$BASE_URL/assistant" \
  -H "Content-Type: application/json" \
  -d "{\"sessionId\":\"$SESSION_ID\",\"message\":\"no\"}" | jq '.state'

# Expected state: CONFIRMACION
curl -s -X POST "$BASE_URL/assistant" \
  -H "Content-Type: application/json" \
  -d "{\"sessionId\":\"$SESSION_ID\",\"message\":\"no\"}" | jq '.state'

# Expected state: SUCCESS
curl -s -X POST "$BASE_URL/assistant" \
  -H "Content-Type: application/json" \
  -d "{\"sessionId\":\"$SESSION_ID\",\"message\":\"confirmo\"}" | jq '.state'
```

## Happy path: manual client + direccion + manoDeObra + no adicionales + confirm

```bash
BASE_URL="http://localhost:8080"
RESP=$(curl -s -X POST "$BASE_URL/assistant" \
  -H "Content-Type: application/json" \
  -d '{"message":"cliente manual"}')
SESSION_ID=$(echo "$RESP" | jq -r '.session_id')
echo "$RESP" | jq '.state'

# Expected state: CAPTURA_CLIENTE_MANUAL
curl -s -X POST "$BASE_URL/assistant" \
  -H "Content-Type: application/json" \
  -d "{\"sessionId\":\"$SESSION_ID\",\"message\":\"Cliente Demo SA\"}" | jq '.state'

# Expected state: CAPTURA_DIRECCION_MANUAL
curl -s -X POST "$BASE_URL/assistant" \
  -H "Content-Type: application/json" \
  -d "{\"sessionId\":\"$SESSION_ID\",\"message\":\"Av. Siempre Viva 123\"}" | jq '.state'

# Expected state: CAPTURA_TRABAJO
curl -s -X POST "$BASE_URL/assistant" \
  -H "Content-Type: application/json" \
  -d "{\"sessionId\":\"$SESSION_ID\",\"message\":\"Mantenimiento A/A\"}" | jq '.state'

# Expected state: CAPTURA_MANO_OBRA
curl -s -X POST "$BASE_URL/assistant" \
  -H "Content-Type: application/json" \
  -d "{\"sessionId\":\"$SESSION_ID\",\"message\":\"12000\"}" | jq '.state'

# Expected state: CAPTURA_MATERIALES_CONFIRM
curl -s -X POST "$BASE_URL/assistant" \
  -H "Content-Type: application/json" \
  -d "{\"sessionId\":\"$SESSION_ID\",\"message\":\"no\"}" | jq '.state'

# Expected state: CAPTURA_EQUIPOS_CONFIRM
curl -s -X POST "$BASE_URL/assistant" \
  -H "Content-Type: application/json" \
  -d "{\"sessionId\":\"$SESSION_ID\",\"message\":\"no\"}" | jq '.state'

# Expected state: CAPTURA_EXTRAS_CONFIRM
curl -s -X POST "$BASE_URL/assistant" \
  -H "Content-Type: application/json" \
  -d "{\"sessionId\":\"$SESSION_ID\",\"message\":\"no\"}" | jq '.state'

# Expected state: SUCCESS
curl -s -X POST "$BASE_URL/assistant" \
  -H "Content-Type: application/json" \
  -d "{\"sessionId\":\"$SESSION_ID\",\"message\":\"si\"}" | jq '.state'
```
