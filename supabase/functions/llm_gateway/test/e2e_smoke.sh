#!/usr/bin/env bash
# Spec 014 — T014-021 live E2E smoke for the deployed Edge Function.
# Sends one request of every type using a real Supabase JWT.

set -euo pipefail

URL="${ORBIT_LLM_URL:-https://orbit-llm-gateway.vercel.app/llm}"
JWT="$(cat /tmp/test_jwt.txt)"

if [[ -z "$JWT" ]]; then
  echo "ERROR: /tmp/test_jwt.txt empty" >&2
  exit 1
fi

call() {
  local label="$1"
  local body="$2"
  local rid
  rid="$(uuidgen | tr '[:upper:]' '[:lower:]')"
  local payload
  payload="$(jq -nc --arg t "$label" --arg rid "$rid" --argjson p "$body" '{type:$t, requestId:$rid, payload:$p}')"
  local start_ms end_ms
  start_ms=$(python3 -c "import time; print(int(time.time()*1000))")
  local code
  code=$(curl -sS -o /tmp/r.json -w "%{http_code}" -X POST "$URL" \
    -H "Authorization: Bearer $JWT" \
    -H "Content-Type: application/json" \
    -d "$payload" --max-time 60)
  end_ms=$(python3 -c "import time; print(int(time.time()*1000))")
  local latency=$((end_ms - start_ms))
  local ok type err
  ok=$(jq -r '.ok' /tmp/r.json 2>/dev/null || echo "?")
  type=$(jq -r '.type' /tmp/r.json 2>/dev/null || echo "?")
  err=$(jq -r '.error // empty | tostring' /tmp/r.json 2>/dev/null || echo "")
  printf "%-22s HTTP=%s ok=%s type=%s latency=%dms" "$label" "$code" "$ok" "$type" "$latency"
  if [[ -n "$err" && "$err" != "null" ]]; then
    printf " err=%s" "$err"
  fi
  printf "\n"
  printf "  rid=%s\n" "$rid"
}

echo "URL=$URL"
echo "jwt_len=${#JWT}"
echo "user_id=$(cat /tmp/test_user_id.txt 2>/dev/null)"
echo "---"

call embed                '{"text":"hello orbit e2e"}'
call summarize            '{"text":"This is a long passage about productivity and habits. Orbit captures the moment.","maxTokens":40}'
call classify_intent      '{"text":"meeting with sarah at 3pm tomorrow","appCategory":"calendar"}'
call generate_day_header  '{"dayIsoDate":"2026-04-29","envelopeSummaries":["Orbit kickoff","Edge Function ships","E2E smoke green"]}'
call scan_sensitivity     '{"text":"my phone number is 555-867-5309"}'
call extract_actions      '{"text":"remind me to email john tomorrow","contentType":"text/plain","state":{"foregroundApp":"messages","appCategory":"messaging","activityState":"foreground","hourLocal":14,"dayOfWeek":"Wednesday"},"registeredFunctions":[{"id":"create_reminder","name":"Create Reminder","schema":{"title":"string","when":"string"}}],"maxCandidates":3}'
