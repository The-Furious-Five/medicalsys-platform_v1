#!/usr/bin/env bash
# Pruebas unitarias y de componente de los microservicios, igual que el job "servicio" del CI
# (./mvnw verify: tests + umbrales de cobertura), pero en local y sin gastar minutos de GitHub.
#
# Cada servicio se compila en un contenedor eclipse-temurin:21 con la cache de Maven compartida
# (volumen medicalsys-m2). Los servicios con Testcontainers usan el Docker del host.
#
#   bash scripts/test/unit.sh                      # los servicios que cambiaron respecto de origin/develop
#   bash scripts/test/unit.sh ms-identidad ms-catalogos
#   bash scripts/test/unit.sh --all
#   PARALLEL=3 BASE=origin/main bash scripts/test/unit.sh
#
# Deja un log por servicio en target/test-logs/ y termina con codigo distinto de 0 si alguno falla.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
PARALLEL="${PARALLEL:-2}"
BASE="${BASE:-origin/develop}"
LOGS="$ROOT/target/test-logs"
mkdir -p "$LOGS"
# En Windows, python3 puede ser el acceso directo vacio de la Microsoft Store: se usa el que funcione.
PY=python3; "$PY" -c "" 2>/dev/null || PY=python

if [ "${1:-}" = "--all" ]; then
  mapfile -t SERVICES < <(ls -d services/ms-*/ | xargs -n1 basename)
elif [ $# -gt 0 ]; then
  SERVICES=("$@")
else
  mapfile -t SERVICES < <("$PY" scripts/ci/changed_services.py "$BASE" HEAD 2>/dev/null \
    | sed -n 's/^services=//p' | "$PY" -c 'import json,sys; print("\n".join(json.load(sys.stdin)))')
fi
[ ${#SERVICES[@]} -gt 0 ] || { echo "No hay servicios que probar."; exit 0; }
echo "Servicios: ${SERVICES[*]} (de a $PARALLEL)"

# En Git Bash de Windows, docker necesita la ruta del host en formato C:/... y sin conversion MSYS.
host_path() { if command -v cygpath >/dev/null; then cygpath -m "$1"; else printf '%s' "$1"; fi; }

run_one() {
  local service="$1" dir log
  dir="$(host_path "$ROOT/services/$service")"
  log="$LOGS/$service.log"
  MSYS_NO_PATHCONV=1 docker run --rm \
    -v "$dir:/workspace" -v medicalsys-m2:/root/.m2 \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -e TESTCONTAINERS_RYUK_DISABLED=true \
    -w /workspace eclipse-temurin:21-jdk-jammy sh ./mvnw -B -ntp verify >"$log" 2>&1
  local code=$?
  local summary
  summary="$("$PY" - "$ROOT/services/$service" <<'PY'
import glob, sys, xml.etree.ElementTree as ET
t = f = e = s = 0
for p in glob.glob(sys.argv[1] + "/target/*-reports/TEST-*.xml"):
    r = ET.parse(p).getroot()
    t += int(r.get("tests")); f += int(r.get("failures")); e += int(r.get("errors")); s += int(r.get("skipped"))
print(f"tests={t} fallos={f} errores={e} omitidos={s}")
PY
)"
  if [ $code -eq 0 ]; then echo "OK    $service  $summary"; else echo "FALLA $service  $summary  (ver $log)"; fi
  return $code
}

failed=0
running=0
pids=()
for service in "${SERVICES[@]}"; do
  run_one "$service" &
  pids+=($!)
  running=$((running + 1))
  if [ $running -ge "$PARALLEL" ]; then
    wait -n || failed=1
    running=$((running - 1))
  fi
done
for _ in $(seq 1 $running); do wait -n || failed=1; done

[ $failed -eq 0 ] && echo "Todas las pruebas en verde." || echo "Hay servicios con fallas."
exit $failed
