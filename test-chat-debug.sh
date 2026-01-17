#!/bin/bash

# Script de debugging - muestra la respuesta JSON completa
# Uso: ./test-chat-debug.sh

BASE_URL="http://localhost:8080"
SESSION_ID="test-session-$(date +%s)"

echo "🔍 Asistente Ri - Modo Debug"
echo "============================"
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
  
  ESCAPED_MESSAGE=$(echo "$USER_MESSAGE" | sed 's/"/\\"/g')
  
  echo ""
  echo "📤 Request:"
  echo "{\"sessionId\":\"$SESSION_ID\",\"message\":\"$ESCAPED_MESSAGE\"}"
  echo ""
  
  RESPONSE=$(curl -s -X POST "$BASE_URL/assistant" \
    -H "Content-Type: application/json" \
    -d "{\"sessionId\":\"$SESSION_ID\",\"message\":\"$ESCAPED_MESSAGE\"}")
  
  echo "📥 Response JSON completa:"
  if command -v jq &> /dev/null; then
    echo "$RESPONSE" | jq '.'
  else
    echo "$RESPONSE"
  fi
  echo ""
  
  if command -v jq &> /dev/null; then
    ASSISTANT_REPLY=$(echo "$RESPONSE" | jq -r '.reply_text // "ERROR"')
    STATE=$(echo "$RESPONSE" | jq -r '.state // "N/A"')
    echo "🤖 Asistente: $ASSISTANT_REPLY"
    echo "📊 Estado: $STATE"
  else
    ASSISTANT_REPLY=$(echo "$RESPONSE" | grep -o '"reply_text":"[^"]*"' | sed 's/"reply_text":"//;s/"$//')
    echo "🤖 Asistente: $ASSISTANT_REPLY"
  fi
  echo ""
done
