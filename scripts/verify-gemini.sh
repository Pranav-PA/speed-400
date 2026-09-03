#!/usr/bin/env bash
# Checks that the Gemini request the app builds actually works with your key.
#
#   GEMINI_API_KEY=... bash scripts/verify-gemini.sh
#
# It sends the SAME payload shape GeminiClient.kt sends: the question text plus the
# tool schema, and nothing else. No data from your bike is involved — there is none
# on this machine. Paste the output back if anything fails.
#
# On Windows use Git Bash, or run the PowerShell version: verify-gemini.ps1
set -uo pipefail

KEY="${GEMINI_API_KEY:-}"
if [ -z "$KEY" ]; then
  echo "Set GEMINI_API_KEY first:"
  echo "  GEMINI_API_KEY=your_key bash scripts/verify-gemini.sh"
  exit 1
fi

BASE="https://generativelanguage.googleapis.com/v1beta"
PASS=0; FAIL=0
ok()   { echo "  PASS  $1"; PASS=$((PASS+1)); }
bad()  { echo "  FAIL  $1"; FAIL=$((FAIL+1)); }

echo
echo "1. Can the key list models? (this is what the model picker calls)"
MODELS=$(curl -sS -H "x-goog-api-key: $KEY" "$BASE/models?pageSize=200" 2>&1)
if echo "$MODELS" | grep -q '"name"'; then
  FLASH=$(echo "$MODELS" | grep -o '"models/gemini[^"]*flash[^"]*"' | tr -d '"' | sed 's|models/||' | sort -u)
  ok "key accepted"
  echo
  echo "  Flash-tier models your key can use — pick one of these in the app:"
  echo "$FLASH" | sed 's/^/    /'
  MODEL=$(echo "$FLASH" | grep -v 'preview\|lite' | head -1)
  [ -z "$MODEL" ] && MODEL=$(echo "$FLASH" | head -1)
else
  bad "could not list models"
  echo "$MODELS" | head -20
  exit 1
fi

echo
echo "2. Does function calling return a tool call? (model: $MODEL)"
echo "   Asking: \"how much have I spent on fuel this year?\""
RESP=$(curl -sS -X POST "$BASE/models/$MODEL:generateContent" \
  -H "x-goog-api-key: $KEY" -H "Content-Type: application/json" \
  -d '{
  "contents":[{"role":"user","parts":[{"text":"how much have I spent on fuel this year?"}]}],
  "tools":[{"functionDeclarations":[
    {"name":"sum_expenses","description":"Total money spent, optionally filtered by category and date range.","parameters":{"type":"OBJECT","properties":{"category":{"type":"STRING","description":"Spend category."},"from":{"type":"STRING","description":"Start date YYYY-MM-DD."},"to":{"type":"STRING","description":"End date YYYY-MM-DD."}}}},
    {"name":"spec_lookup","description":"A specification from the owner handbook.","parameters":{"type":"OBJECT","properties":{"query":{"type":"STRING","description":"What specification is wanted."}}}}
  ]}],
  "systemInstruction":{"parts":[{"text":"You route questions about a Triumph Speed 400 to exactly one tool. Never answer in prose and never state a figure yourself."}]},
  "generationConfig":{"temperature":0}
}' 2>&1)

if echo "$RESP" | grep -q '"functionCall"'; then
  ok "returned a functionCall"
  echo "  It chose:"
  echo "$RESP" | tr ',' '\n' | grep -A2 -i 'functionCall\|"name"\|category\|from' | sed 's/^/    /' | head -12
  echo "$RESP" | grep -q 'sum_expenses' && ok "chose sum_expenses (correct)" || bad "did NOT choose sum_expenses"
else
  bad "no functionCall in the response — the payload shape or the model is wrong"
  echo "$RESP" | head -30
fi

echo
echo "3. Does a handbook question route differently?"
echo "   Asking: \"what is the rear tyre pressure?\""
RESP2=$(curl -sS -X POST "$BASE/models/$MODEL:generateContent" \
  -H "x-goog-api-key: $KEY" -H "Content-Type: application/json" \
  -d '{
  "contents":[{"role":"user","parts":[{"text":"what is the rear tyre pressure?"}]}],
  "tools":[{"functionDeclarations":[
    {"name":"sum_expenses","description":"Total money spent.","parameters":{"type":"OBJECT","properties":{"category":{"type":"STRING","description":"Category."}}}},
    {"name":"spec_lookup","description":"A specification from the owner handbook - tyre pressures, oil grade, torque figures.","parameters":{"type":"OBJECT","properties":{"query":{"type":"STRING","description":"What specification is wanted."}}}}
  ]}],
  "generationConfig":{"temperature":0}
}' 2>&1)
echo "$RESP2" | grep -q 'spec_lookup' && ok "chose spec_lookup (correct)" || bad "did not choose spec_lookup"

echo
echo "-------------------------------------------"
echo "  $PASS passed, $FAIL failed"
if [ "$FAIL" -eq 0 ]; then
  echo "  All good. Put the key and model into the app's Ask tab."
else
  echo "  Paste this output back and I'll fix it."
fi
echo
echo "  NOTE: your key was used only against Google here. It is not written"
echo "  to any file by this script."
