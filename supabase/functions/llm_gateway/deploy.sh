#!/usr/bin/env bash
# Spec 014 FR-014-020 — Vercel deploy runbook script.
# Verifies all five required env vars are set in Vercel `production`
# before invoking `vercel deploy --prod`. Exits non-zero on any missing var.
#
# Usage:
#   bash deploy.sh           # verify + deploy
#   bash deploy.sh --dry-run # verify only (no deploy)
#
# Prerequisites: `vercel` CLI logged in and project linked (see README.md).

set -euo pipefail

REQUIRED_VARS=(
  OPENAI_API_KEY
  ANTHROPIC_API_KEY
  SUPABASE_SERVICE_ROLE_KEY
  SUPABASE_URL
  SUPABASE_JWT_SECRET
)

DRY_RUN=0
if [[ "${1-}" == "--dry-run" ]]; then
  DRY_RUN=1
fi

echo "==> Verifying required env vars in Vercel production..."

# `vercel env ls production` prints a table; grep each required var.
ENV_LS=$(vercel env ls production 2>&1 || true)

missing=()
for v in "${REQUIRED_VARS[@]}"; do
  if ! grep -qE "^\s*${v}\b" <<<"$ENV_LS"; then
    missing+=("$v")
  fi
done

if (( ${#missing[@]} > 0 )); then
  echo "ERROR: Missing required Vercel production env vars:"
  for v in "${missing[@]}"; do
    echo "  - $v"
  done
  echo
  echo "Set with:  vercel env add <NAME> production"
  exit 1
fi

echo "OK: all 5 required env vars present in production."

if (( DRY_RUN )); then
  echo "==> --dry-run: skipping deploy."
  exit 0
fi

echo "==> Deploying to Vercel production..."
vercel deploy --prod
