#!/bin/bash

# Script para probar el Asistente Ri desde la consola
# Uso: ./test-chat.sh

BASE_URL="http://localhost:8080"
SESSION_ID="test-session-$(date +%s)"

echo "🤖 Asistente Ri - Chat de Prueba"
echo "================================"
echo "Session ID: $SESSION_ID"
echo "Escribe 'salir' para terminar"
echo ""

while true; do
  echo -n "Tú: "
  read USER_MESSAGE
  
  if [ "$USER_MESSAGE" = "salir" ]; then
    echo "¡Hasta luego!"
    break
  fi
  
  if [ -z "$USER_MESSAGE" ]; then
    continue
  fi
  
  # Escapar comillas en el mensaje
  ESCAPED_MESSAGE=$(echo "$USER_MESSAGE" | sed 's/"/\\"/g')
  
  # Hacer request al asistente
  RESPONSE=$(curl -s -X POST "$BASE_URL/assistant" \
    -H "Content-Type: application/json" \
    -d "{\"sessionId\":\"$SESSION_ID\",\"message\":\"$ESCAPED_MESSAGE\"}")
  
  # Extraer el mensaje de respuesta (campo correcto: reply_text)
  if command -v jq &> /dev/null; then
    ASSISTANT_REPLY=$(echo "$RESPONSE" | jq -r '.reply_text // "ERROR: No hay respuesta"')
  else
    ASSISTANT_REPLY=$(echo "$RESPONSE" | grep -o '"reply_text":"[^"]*"' | sed 's/"reply_text":"//;s/"$//')
    if [ -z "$ASSISTANT_REPLY" ]; then
      ASSISTANT_REPLY="ERROR: No se pudo extraer mensaje. Respuesta: $RESPONSE"
    fi
  fi
  
  echo "🤖 Asistente: $ASSISTANT_REPLY"
  echo ""
done
