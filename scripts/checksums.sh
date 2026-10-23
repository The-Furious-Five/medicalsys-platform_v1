#!/usr/bin/env bash
# CRY-03: genera y verifica SHA256SUMS para los artefactos de entrega.
#
# Uso:
#   ./scripts/checksums.sh generate [imagen1 imagen2 ...]   # crea SHA256SUMS
#   ./scripts/checksums.sh verify                           # corre sha256sum -c
#   ./scripts/checksums.sh --compare <archivo>               # muestra SHA256 vs CRC32 vs MD5
#
# Qué incluye "generate" (solo si existe; si no, avisa y sigue):
#   - JARs de cada microservicio:            services/*/target/*.jar
#   - Imágenes Docker relevantes:            las medicalsys/*:local de compose.yaml,
#                                             detectadas con `docker images` si docker
#                                             está disponible; si no, se pueden pasar
#                                             nombres de imagen como argumentos extra.
#     (las imágenes se listan en un archivo aparte docker-images.sha256 porque
#      `sha256sum -c` solo sabe verificar archivos, no "docker images")
#   - Export de Keycloak (realm-export.json o similar bajo infra/ o docs/): NO EXISTE
#     en este repo a la fecha; si aparece luego, este script lo recoge solo.
#   - PDFs de entrega: *.pdf en la raíz del repo y en docs/ (recursivo).
#
# Test manual (criterio de aceptación: detectar 1 byte modificado):
#   ./scripts/checksums.sh generate
#   ./scripts/checksums.sh verify                 # -> OK
#   printf 'x' >> docs/contracts/routes-v1.md      # modifica 1 byte
#   ./scripts/checksums.sh verify                 # -> FAILED, exit code != 0
#   git checkout -- docs/contracts/routes-v1.md    # revertir el cambio de prueba

set -u
cd "$(dirname "$0")/.."

OUT="SHA256SUMS"
IMG_OUT="docker-images.sha256"

collect_files() {
  local files=()

  # JARs de cada microservicio
  local jar found_jar=0
  while IFS= read -r jar; do
    [ -n "$jar" ] || continue
    files+=("$jar")
    found_jar=1
  done < <(find services -path "*/target/*.jar" 2>/dev/null)
  if [ "$found_jar" -eq 0 ]; then
    echo "AVISO: no se encontraron JARs compilados en services/*/target/*.jar (¿falta compilar?)" >&2
  fi

  # PDFs de entrega (raiz + docs/)
  local pdf found_pdf=0
  while IFS= read -r pdf; do
    [ -n "$pdf" ] || continue
    files+=("$pdf")
    found_pdf=1
  done < <(find . -maxdepth 1 -iname "*.pdf" 2>/dev/null; find docs -iname "*.pdf" 2>/dev/null)
  if [ "$found_pdf" -eq 0 ]; then
    echo "AVISO: no se encontraron PDFs de entrega en la raíz ni en docs/" >&2
  fi

  # Export de Keycloak (realm-export.json o similar) bajo infra/ o docs/
  local realm found_realm=0
  while IFS= read -r realm; do
    [ -n "$realm" ] || continue
    files+=("$realm")
    found_realm=1
  done < <(find infra docs -iname "*realm*export*.json" -o -iname "*realm-export*" 2>/dev/null)
  if [ "$found_realm" -eq 0 ]; then
    echo "AVISO: no se encontró export de realm de Keycloak (realm-export.json) bajo infra/ ni docs/. Se omite." >&2
  fi

  printf '%s\n' "${files[@]}"
}

generate() {
  local files
  mapfile -t files < <(collect_files)

  if [ "${#files[@]}" -eq 0 ]; then
    echo "No se encontró ningún archivo para sumar. No se crea $OUT." >&2
  else
    printf '%s\0' "${files[@]}" | xargs -0 sha256sum > "$OUT"
    echo "Generado $OUT con ${#files[@]} archivo(s)."
  fi

  # Imágenes Docker (van en archivo aparte: no son archivos locales)
  local images=("$@")
  if [ "${#images[@]}" -eq 0 ] && command -v docker >/dev/null 2>&1; then
    # Imágenes locales medicalsys/*:local definidas en compose.yaml
    mapfile -t images < <(docker images --format '{{.Repository}}:{{.Tag}}' 2>/dev/null | grep -i '^medicalsys/')
  fi
  if [ "${#images[@]}" -eq 0 ]; then
    echo "AVISO: no se encontraron imágenes Docker (docker no disponible, o ninguna imagen medicalsys/*:local construida). Pasa nombres como argumentos si quieres sumarlas: $0 generate medicalsys/ms-identidad:local ..." >&2
  else
    : > "$IMG_OUT"
    for img in "${images[@]}"; do
      local id
      id=$(docker inspect --format '{{.Id}}' "$img" 2>/dev/null) || { echo "AVISO: no se pudo inspeccionar la imagen $img" >&2; continue; }
      echo "$id  $img" >> "$IMG_OUT"
    done
    echo "Generado $IMG_OUT con los IDs (sha256) de ${#images[@]} imagen(es)."
  fi
}

verify() {
  if [ ! -f "$OUT" ]; then
    echo "No existe $OUT. Corre primero: $0 generate" >&2
    exit 1
  fi
  sha256sum -c "$OUT"
  local rc=$?
  if [ -f "$IMG_OUT" ] && command -v docker >/dev/null 2>&1; then
    echo "--- verificando imágenes Docker ---"
    while IFS=' ' read -r expected_id img; do
      [ -n "$img" ] || continue
      current_id=$(docker inspect --format '{{.Id}}' "$img" 2>/dev/null)
      if [ "$current_id" = "$expected_id" ]; then
        echo "$img: OK"
      else
        echo "$img: FAILED (esperado $expected_id, actual ${current_id:-<no existe>})"
        rc=1
      fi
    done < "$IMG_OUT"
  fi
  exit $rc
}

compare() {
  local f="$1"
  if [ ! -f "$f" ]; then
    echo "Archivo no encontrado: $f" >&2
    exit 1
  fi
  echo "Archivo: $f"
  echo "SHA256 (criptográfico, resistente a colisiones intencionales): $(sha256sum "$f" | cut -d' ' -f1)"
  echo "MD5    (criptográfico pero roto, solo para detección casual):  $(md5sum "$f" | cut -d' ' -f1)"
  if command -v cksum >/dev/null 2>&1; then
    # cksum por defecto usa CRC32 (POSIX); -o 3 = legacy, por eso no se pasa flag.
    echo "CRC32  (checksum simple, solo detecta errores aleatorios):     $(cksum "$f" | cut -d' ' -f1)"
  else
    echo "CRC32: 'cksum' no disponible en este sistema."
  fi
}

cmd="${1:-}"
case "$cmd" in
  generate)
    shift
    generate "$@"
    ;;
  verify)
    verify
    ;;
  --compare)
    [ -n "${2:-}" ] || { echo "Uso: $0 --compare <archivo>" >&2; exit 1; }
    compare "$2"
    ;;
  *)
    echo "Uso: $0 {generate|verify|--compare <archivo>}" >&2
    exit 1
    ;;
esac
