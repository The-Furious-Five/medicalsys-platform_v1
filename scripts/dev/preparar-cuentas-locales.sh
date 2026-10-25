#!/usr/bin/env bash
# Solo para el entorno de desarrollo local: deja todas las cuentas sembradas (las del centro demo y la de
# plataforma) listas para probar. El OSI reinicia cada una, se le pone una contrasena propia que cumple la
# politica y se registra su segundo factor. Usuarios, contrasenas y secretos TOTP quedan en
# cuentas-locales.local.md (ignorado por git); el codigo del momento lo da scripts/dev/codigo.sh.
#
#   bash scripts/dev/preparar-cuentas-locales.sh
#
# Usa la cuenta OSI del .env (OSI_PASSWORD, OSI_TOTP_SECRET y OSI_USERNAME si no es la de por defecto).
# Nunca correr contra el VPS: cierra las sesiones y cambia las contrasenas de esas cuentas.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
env_value() { grep -E "^$1=" .env 2>/dev/null | tail -1 | cut -d= -f2-; }
host_path() { if command -v cygpath >/dev/null; then cygpath -m "$1"; else printf '%s' "$1"; fi; }
OUT_FILE="$ROOT/cuentas-locales.local.md"

out="$(mktemp -d)"
MSYS_NO_PATHCONV=1 docker run --rm -i --network medicalsys-interna \
  -v "$(host_path "$ROOT"):/repo:ro" -v "$(host_path "$out"):/out" -w /repo \
  -e OSI_USERNAME="$(env_value OSI_USERNAME)" -e OSI_PASSWORD="$(env_value OSI_PASSWORD)" \
  -e OSI_TOTP_SECRET="$(env_value OSI_TOTP_SECRET)" \
  alpine:3.22 sh -c 'apk add -q --no-cache bash curl jq openssl oath-toolkit-oathtool >/dev/null && bash -s' <<'INNER'
set -uo pipefail
B=http://ms-gateway:8080/api/v1/identity
J='Content-Type: application/json'
source services/ms-identidad/scripts/lib-mfa.sh
OSI_USERNAME="${OSI_USERNAME:-oficial.seguridad.i}"
osi="$(mfa_login "$OSI_USERNAME" "${OSI_PASSWORD:-}" "${OSI_TOTP_SECRET:-}")"
[ -n "$osi" ] || { echo "No se pudo entrar como OSI ($OSI_USERNAME): revisa OSI_PASSWORD y OSI_TOTP_SECRET en .env" >&2; exit 1; }
OSI="Authorization: Bearer $osi"

{
  echo "# Cuentas locales de prueba"
  echo
  echo "Solo para el entorno local. Generado por scripts/dev/preparar-cuentas-locales.sh el $(date '+%Y-%m-%d %H:%M')."
  echo "Codigo del segundo factor: \`bash scripts/dev/codigo.sh <usuario>\` (o carga el secreto en Google Authenticator)."
  echo "scripts/test/e2e.sh vuelve a reiniciar administrador, medico y cajero: tras correrlo, vuelve a generar este archivo."
  echo
  echo "| Usuario | Rol | Correo | Contrasena | Secreto TOTP |"
  echo "|---|---|---|---|---|"
  echo "| $OSI_USERNAME | OSI | (cuenta de arranque) | OSI_PASSWORD del .env | OSI_TOTP_SECRET del .env |"
} > /out/cuentas.md

# Cuentas sembradas: identificador publico fijo 00000000-0000-4000-8000-0000000009xx (la 09ff es el OSI).
accounts="$(curl -s "$B/users?size=500" -H "$OSI" | jq -r '.content[]
  | select(.publicId | test("^00000000-0000-4000-8000-0000000009")) | select(.publicId != "00000000-0000-4000-8000-0000000009ff")
  | select(.status != "INACTIVE") | [.publicId, .username, .email, ([.roles[]?.role] | join(", "))] | @tsv')"
ok=0; failed=0
while IFS=$'\t' read -r id username email roles; do
  [ -n "$id" ] || continue
  # Una cuenta bloqueada por intentos se desbloquea antes de reiniciarla.
  curl -s -o /dev/null -X POST "$B/users/$id/unlock" -H "$OSI"
  temp="$(curl -s -X POST "$B/users/$id/reset-password" -H "$OSI" | jq -r '.temporaryPassword // empty')"
  password="Local-$(openssl rand -hex 4)-Prueba9!"
  changed="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$B/auth/change-password" -H "$J" \
    -d "$(jq -n --arg u "$username" --arg c "$temp" --arg n "$password" '{username: $u, currentPassword: $c, newPassword: $n}')")"
  read -r secret token <<<"$(mfa_enroll "$username" "$password" 2>/dev/null)"
  if [ "$changed" = 200 ] && [ -n "${token:-}" ]; then
    echo "| $username | $roles | $email | \`$password\` | \`$secret\` |" >> /out/cuentas.md
    echo "  lista: $username ($roles)"; ok=$((ok + 1))
  else
    echo "  FALLO: $username ($roles)"; failed=$((failed + 1))
  fi
done <<<"$accounts"
echo "$ok cuentas listas, $failed con problemas"
INNER
rc=$?
if [ -s "$out/cuentas.md" ]; then
  cp "$out/cuentas.md" "$OUT_FILE"
  echo "Usuarios, contrasenas y secretos en: $OUT_FILE"
fi
rm -rf "$out"
exit $rc
