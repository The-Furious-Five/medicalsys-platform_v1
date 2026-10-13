#!/usr/bin/env bash
# IAM-02: demuestra cada regla de la política de contraseñas y el bloqueo al 3er intento
# contra un Keycloak en ejecución.
#
#   set -a; source .env; set +a
#   bash infra/keycloak/policy-check.sh
#
# Usa la service account de ms-identidad (manage-users), como lo hará la aplicación. Crea un
# usuario de prueba y lo borra al terminar. Las contraseñas de los casos inválidos son
# ejemplos fijos; las válidas se generan en cada corrida y no se imprimen.
set -euo pipefail

KC_URL="${KC_URL:-http://localhost:${KEYCLOAK_HOST_PORT:-18080}}"
REALM="${REALM:-medicalsys}"
CLIENT_ID="ms-identidad"
CLIENT_SECRET="${KEYCLOAK_MS_IDENTIDAD_CLIENT_SECRET:?falta KEYCLOAK_MS_IDENTIDAD_CLIENT_SECRET (source .env)}"
TOKEN_URL="$KC_URL/realms/$REALM/protocol/openid-connect/token"
ADMIN_URL="$KC_URL/admin/realms/$REALM"

stamp="$(date +%s)"
USERNAME="iam02.prueba-${stamp}x"
EMAIL="iam02.correo-${stamp}x@medicalsys.invalid"
user_id=""
failures=0

fail() { printf 'FALLA: %s\n' "$1" >&2; failures=$((failures + 1)); }
token() {
  curl -sS -X POST "$TOKEN_URL" -d grant_type=client_credentials -d client_id="$CLIENT_ID" \
    --data-urlencode client_secret="$CLIENT_SECRET" | jq -r '.access_token // empty'
}
service_token="$(token)"
[ -n "$service_token" ] || { echo "No se obtuvo el token del cliente ms-identidad" >&2; exit 1; }
auth=(-H "Authorization: Bearer $service_token")

cleanup() {
  [ -n "$user_id" ] && curl -sS -o /dev/null -X DELETE "${auth[@]}" "$ADMIN_URL/users/$user_id" || true
}
trap cleanup EXIT

valid_password() { printf 'Prueba valida %s Ok9!' "$(openssl rand -hex 5)"; }

# Fija la contraseña por la Admin REST API (reset-password) y devuelve "código|mensaje".
set_password() {
  local body response
  body="$(jq -n --arg p "$1" '{type: "password", value: $p, temporary: false}')"
  response="$(curl -sS -w '\n%{http_code}' -X PUT "${auth[@]}" -H 'Content-Type: application/json' \
    "$ADMIN_URL/users/$user_id/reset-password" -d "$body")"
  printf '%s|%s' "$(tail -n1 <<<"$response")" "$(sed '$d' <<<"$response" | jq -r '.errorMessage // .error // empty' 2>/dev/null)"
}

login() { # devuelve el error del token endpoint, o "ok"
  curl -sS -X POST "$TOKEN_URL" -d grant_type=password -d client_id="$CLIENT_ID" \
    --data-urlencode client_secret="$CLIENT_SECRET" --data-urlencode username="$USERNAME" \
    --data-urlencode password="$1" | jq -r 'if .access_token then "ok" else (.error + ": " + .error_description) end'
}

echo "== Política del realm"
admin_view="$(curl -sS "${auth[@]}" "$ADMIN_URL/users?max=1" -o /dev/null -w '%{http_code}')"
[ "$admin_view" = "200" ] || fail "la service account no puede listar usuarios ($admin_view)"

echo "== Usuario de prueba $USERNAME"
status="$(curl -sS -o /dev/null -w '%{http_code}' -D /tmp/iam02-headers.$$ -X POST "${auth[@]}" \
  -H 'Content-Type: application/json' "$ADMIN_URL/users" \
  -d "$(jq -n --arg u "$USERNAME" --arg e "$EMAIL" '{username: $u, email: $e, firstName: "Prueba",
        lastName: "IAM-02", enabled: true, emailVerified: true}')")"
[ "$status" = "201" ] || { echo "No se pudo crear el usuario de prueba ($status)" >&2; exit 1; }
user_id="$(grep -i '^location:' /tmp/iam02-headers.$$ | tr -d '\r' | sed 's#.*/users/##')"
rm -f /tmp/iam02-headers.$$

echo
echo "== Reglas: cada contraseña inválida se rechaza con su mensaje"
printf '%-34s %-6s %s\n' "Regla" "HTTP" "Mensaje de Keycloak"
check_rejected() { # regla, contraseña
  local result code message
  result="$(set_password "$2")"; code="${result%%|*}"; message="${result#*|}"
  printf '%-34s %-6s %s\n' "$1" "$code" "$message"
  [ "$code" = "400" ] || fail "$1: se esperaba 400 y Keycloak respondió $code"
}
check_rejected "Mínimo 12 caracteres"              "Corta9!aB"
check_rejected "Máximo 64 caracteres"              "$(printf 'Larga9!%.0s' {1..10})"
check_rejected "Al menos 1 mayúscula"              "sin mayusculas 9!"
check_rejected "Al menos 1 minúscula"              "SIN MINUSCULAS 9!"
check_rejected "Al menos 1 dígito"                 "Sin digitos aqui!"
check_rejected "Al menos 1 carácter especial"      "SinEspeciales9X"
check_rejected "Distinta del nombre de usuario"    "$(tr 'a-z' 'A-Z' <<<"${USERNAME:0:1}")${USERNAME:1}"
check_rejected "Distinta del correo"               "$(tr 'a-z' 'A-Z' <<<"${EMAIL:0:1}")${EMAIL:1}"
check_rejected "Lista negra"                       "Medicalsys2026!"

echo
echo "== Histórico: no se reutilizan las últimas 5"
first="$(valid_password)"
result="$(set_password "$first")"; [ "${result%%|*}" = "204" ] || fail "una contraseña válida respondió ${result%%|*}"
echo "Contraseña válida aceptada: ${result%%|*}"
result="$(set_password "$(valid_password)")"; [ "${result%%|*}" = "204" ] || fail "la segunda contraseña válida respondió ${result%%|*}"
result="$(set_password "$first")"
printf '%-34s %-6s %s\n' "Reutilizar la anterior" "${result%%|*}" "${result#*|}"
[ "${result%%|*}" = "400" ] || fail "reutilizar una contraseña del histórico respondió ${result%%|*}"

echo
echo "== Almacenamiento: algoritmo del hash"
curl -sS "${auth[@]}" "$ADMIN_URL/users/$user_id/credentials" \
  | jq -r '.[] | select(.type == "password") | .credentialData | fromjson | "algorithm=\(.algorithm), \(.additionalParameters // {} | tostring)"'

echo
echo "== Bloqueo al 3er intento fallido"
current="$(valid_password)"
result="$(set_password "$current")"; [ "${result%%|*}" = "204" ] || fail "no se pudo fijar la contraseña para el bloqueo"
echo "Antes: $(login "$current")"
for i in 1 2 3; do echo "Intento fallido $i: $(login "Incorrecta $i Xx9!")"; sleep 1.2; done
echo "Contraseña correcta después de 3 fallos: $(login "$current")"
state="$(curl -sS "${auth[@]}" "$ADMIN_URL/attack-detection/brute-force/users/$user_id")"
echo "Detección de fuerza bruta: $(jq -c '{disabled, numFailures}' <<<"$state")"
[ "$(jq -r .disabled <<<"$state")" = "true" ] || fail "la cuenta no quedó bloqueada"
[ "$(login "$current")" != "ok" ] || fail "la cuenta bloqueada pudo iniciar sesión"

echo
if [ "$failures" -eq 0 ]; then
  echo "OK: todas las reglas rechazan y la cuenta se bloquea al 3er intento."
else
  echo "$failures comprobaciones fallaron." >&2
  exit 1
fi
