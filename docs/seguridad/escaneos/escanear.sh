#!/usr/bin/env bash
# Escaneo de vulnerabilidades de MedicalSys (VULN-01 / VULN-02, procedimiento PR-04).
#
# Ejecuta siempre las mismas tres herramientas con la misma configuracion y deja los reportes
# fechados en docs/seguridad/escaneos/<fase>/:
#   1. Trivy filesystem sobre los pom.xml de services/.
#   2. Trivy image sobre cada imagen medicalsys/*:<tag>.
#   3. OWASP ZAP Baseline (zap-baseline.py) contra el gateway; deja tambien el plan zap.yaml usado.
#
# Uso (desde la raiz del repositorio, con el gateway levantado):
#   bash docs/seguridad/escaneos/escanear.sh <antes|despues> [tag de imagen=local] [raiz del codigo=.]
#
# PASOS="fs image zap" (por defecto los tres) permite repetir solo una parte, p. ej. PASOS=zap.
# ZAP_URL es el gateway visto desde el contenedor de ZAP (por defecto http://host.docker.internal:8080).
#
# Requiere Docker. No necesita Trivy ni ZAP instalados: se usan sus imagenes oficiales.
set -euo pipefail
export MSYS_NO_PATHCONV=1   # Git Bash: no reescribir las rutas de los contenedores

FASE=${1:?Uso: escanear.sh <antes|despues> [tag] [raiz]}
TAG=${2:-local}
RAIZ=$(cd "${3:-.}" && pwd)
FECHA=$(date +%Y%m%d)
PASOS=${PASOS:-fs image zap}
ZAP_URL=${ZAP_URL:-http://host.docker.internal:8080}
TRIVY=aquasec/trivy:0.69.3
ZAP=ghcr.io/zaproxy/zaproxy:stable
# --timeout: la primera ejecucion descarga las bases de vulnerabilidades (~3 GB) dentro del mismo plazo.
OPCIONES=(--scanners vuln --severity HIGH,CRITICAL --format json --quiet --timeout 30m)

SALIDA="$(cd "$(dirname "$0")" && pwd)/$FASE"
TARS=$(mktemp -d)
trap 'rm -rf "$TARS"' EXIT
mkdir -p "$SALIDA"

# Docker Desktop en Windows necesita rutas C:\..., no /c/...
ruta() { if command -v cygpath >/dev/null; then cygpath -w "$1"; else echo "$1"; fi; }

if [[ " $PASOS " == *" fs "* ]]; then
echo "== Trivy filesystem ($RAIZ)"
docker run --rm -v medicalsys-trivy-cache:/root/.cache \
  -v "$(ruta "$RAIZ"):/work:ro" -v "$(ruta "$SALIDA"):/out" "$TRIVY" \
  fs "${OPCIONES[@]}" --skip-dirs '**/target' --skip-dirs node_modules \
  --output "/out/trivy-fs-$FASE-$FECHA.json" /work
fi

if [[ " $PASOS " == *" image "* ]]; then
echo "== Trivy image (medicalsys/*:$TAG)"
docker image ls --format '{{.Repository}}:{{.Tag}}' | grep "^medicalsys/.*:$TAG\$" | sort | while read -r imagen; do
  servicio=${imagen#medicalsys/}; servicio=${servicio%%:*}
  echo "   $imagen"
  docker save "$imagen" -o "$(ruta "$TARS/$servicio.tar")"
  docker run --rm -v medicalsys-trivy-cache:/root/.cache \
    -v "$(ruta "$TARS"):/images:ro" -v "$(ruta "$SALIDA"):/out" "$TRIVY" \
    image "${OPCIONES[@]}" --input "/images/$servicio.tar" \
    --output "/out/trivy-image-$servicio-$FASE-$FECHA.json"
  rm -f "$TARS/$servicio.tar"
done
fi

if [[ " $PASOS " == *" zap "* ]]; then
echo "== OWASP ZAP Baseline ($ZAP_URL)"
# zap-baseline.py instala las reglas pasivas beta y escribe en la carpeta el plan zap.yaml que ejecuto.
# Sale con 1 o 2 cuando hay alertas: eso es un resultado del escaneo, no un fallo del script.
docker run --rm --user root -v "$(ruta "$SALIDA"):/zap/wrk:rw" "$ZAP" \
  zap-baseline.py -t "$ZAP_URL/actuator/health" -m 1 \
  -J "zap-baseline-$FASE-$FECHA.json" -r "zap-baseline-$FASE-$FECHA.html" || [ $? -le 2 ]
fi

echo "Reportes de la fase '$FASE' en $SALIDA"
