#!/usr/bin/env bash
# Read-only smoke test of a running EventHub. Creates no data.
# Usage: ./deploy/smoke-test.sh https://your-site [extra curl args, e.g. -H "Host: example.com"]
set -uo pipefail

BASE="${1:?usage: smoke-test.sh BASE_URL [curl args]}"
shift
CURL=(curl -sS --max-time 15 "$@")
fails=0

check() { # name, expected status, path, [grep pattern]
  local name=$1 want=$2 path=$3 pattern=${4:-}
  local body status
  body=$("${CURL[@]}" -w '\n%{http_code}' "$BASE$path" 2>&1)
  status=${body##*$'\n'}
  body=${body%$'\n'*}
  if [ "$status" != "$want" ]; then
    printf '  FAIL  %-34s expected %s, got %s\n' "$name" "$want" "$status"; fails=$((fails + 1)); return
  fi
  if [ -n "$pattern" ] && ! grep -q -- "$pattern" <<<"$body"; then
    printf '  FAIL  %-34s response did not contain %s\n' "$name" "$pattern"; fails=$((fails + 1)); return
  fi
  printf '  ok    %-34s %s\n' "$name" "$status"
}

# The backend may still be finishing start-up: retry the health check for up to ~2 minutes.
for _ in $(seq 1 24); do
  "${CURL[@]}" -o /dev/null -w '%{http_code}' "$BASE/api/v1/health" 2>/dev/null | grep -q 200 && break
  sleep 5
done

check "API health (backend + database)" 200 /api/v1/health '"database":"UP"'
check "Home page (frontend)"            200 /               'id="root"'
check "Deep link served by SPA"         200 /events/anything 'id="root"'
check "Categories"                      200 /api/v1/categories '"slug":"music"'
check "Public event search"             200 "/api/v1/events?size=1" '"totalElements"'
check "Protected endpoint needs login"  401 /api/v1/bookings/me
check "Actuator is not public"          200 /actuator/health 'id="root"'

if [ "$fails" -gt 0 ]; then
  echo "Smoke test FAILED ($fails check(s))."; exit 1
fi
echo "Smoke test passed."
