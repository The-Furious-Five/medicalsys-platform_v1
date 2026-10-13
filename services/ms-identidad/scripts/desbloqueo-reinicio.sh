#!/usr/bin/env bash
# IAM-05: demostración completa del criterio de aceptación, con curl contra ms-identidad.
#
#   bash services/ms-identidad/scripts/desbloqueo-reinicio.sh http://localhost:8081
#
# 3 intentos bloquean -> se desbloquea desde la app -> reinicio -> cambio forzado ->
# reutilizar una contraseña anterior es rechazado. Las contraseñas no se imprimen.
set -euo pipefail

B="${1:-http://localhost:8081}/api/v1/identity"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-${IDENTITY_DEMO_PASSWORD:-Demo1234!}}"
JSON='Content-Type: application/json'
EMAIL="iam05.prueba.$(date +%s)@medicalsys.demo"
FIRST="Primera clave $(openssl rand -hex 3) ok!"
SECOND="Segunda clave $(openssl rand -hex 3) ok!"

post() { # ruta, cuerpo JSON, [token] -> "HTTP <código> <code> · <detalle>"
  local response
  response="$(curl -sS -w '\n%{http_code}' -X POST "$B$1" -H "$JSON" ${3:+-H "Authorization: Bearer $3"} -d "$2")"
  printf 'HTTP %s  %s\n' "$(tail -n1 <<<"$response")" \
    "$(sed '$d' <<<"$response" | jq -r 'if .accessToken then "acceso concedido" elif .status == "password-updated" then "contraseña actualizada" elif .temporaryPassword then "temporal emitida para \(.userCode)" else "\(.code // "-") · \(.detail // .title // "")" end')"
}
login_body() { jq -n --arg e "$EMAIL" --arg p "$1" '{email: $e, password: $p}'; }
change_body() { jq -n --arg e "$EMAIL" --arg c "$1" --arg p "$2" '{email: $e, currentPassword: $c, newPassword: $p}'; }

admin="$(curl -sS -X POST "$B/auth/login" -H "$JSON" \
  -d "$(jq -n --arg p "$ADMIN_PASSWORD" '{email: "admin@medicalsys.demo", password: $p}')" | jq -r .accessToken)"
created="$(curl -sS -X POST "$B/users" -H "Authorization: Bearer $admin" -H "$JSON" \
  -d "$(jq -n --arg e "$EMAIL" '{email: $e, fullName: "Prueba IAM-05", activateImmediately: true, roles: [{role: "RECEPTIONIST"}]}')")"
user_id="$(jq -r .publicId <<<"$created")"
echo "Cuenta $(jq -r .userCode <<<"$created") ($EMAIL)"
echo "Primer ingreso, cambio de la temporal: $(post /auth/change-password "$(change_body "$(jq -r .temporaryPassword <<<"$created")" "$FIRST")")"

echo; echo "== 1. Tres intentos fallidos bloquean la cuenta"
for i in 1 2 3; do echo "   Intento $i: $(post /auth/login "$(login_body "Incorrecta $i Xx9!")")"; sleep 1.2; done
echo "   Contraseña correcta: $(post /auth/login "$(login_body "$FIRST")")"

echo; echo "== 2. El administrador desbloquea desde la app (identity.user.unlock)"
echo "   $(post "/users/$user_id/unlock" '{}' "$admin" | sed 's/  - · $/  cuenta desbloqueada/')"
echo "   Login: $(post /auth/login "$(login_body "$FIRST")")"

echo; echo "== 3. Reinicio con contraseña temporal (identity.user.reset)"
reset="$(curl -sS -X POST "$B/users/$user_id/reset-password" -H "Authorization: Bearer $admin")"
echo "   Temporal emitida para $(jq -r .userCode <<<"$reset") ($(jq -r '.temporaryPassword | length' <<<"$reset") caracteres, se muestra una sola vez)"
temporary="$(jq -r .temporaryPassword <<<"$reset")"
echo "   Login con la temporal: $(post /auth/login "$(login_body "$temporary")")"

echo; echo "== 4. Cambio forzado: reutilizar la contraseña anterior se rechaza"
echo "   Reutilizar la anterior: $(post /auth/change-password "$(change_body "$temporary" "$FIRST")")"
echo "   Una nueva:              $(post /auth/change-password "$(change_body "$temporary" "$SECOND")")"
echo "   Login con la nueva:     $(post /auth/login "$(login_body "$SECOND")")"
