#!/usr/bin/env bash
# IAM-05: demostración completa del criterio de aceptación, con curl contra ms-identidad.
#
#   bash services/ms-identidad/scripts/desbloqueo-reinicio.sh http://localhost:8081
#
# 3 intentos bloquean -> se desbloquea desde la app -> reinicio -> cambio forzado ->
# reutilizar una contraseña anterior es rechazado -> el reinicio también pide registrar de nuevo el
# segundo factor (IAM-07). Las contraseñas y los secretos no se imprimen.
set -euo pipefail

B="${1:-http://localhost:8081}/api/v1/identity"
# IAM-07: el administrador entra con su usuario, su contraseña y el secreto TOTP de su aplicación.
ADMIN_USERNAME="${ADMIN_USERNAME:?Falta ADMIN_USERNAME, el usuario del administrador del centro}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:?Falta ADMIN_PASSWORD}"
ADMIN_TOTP_SECRET="${ADMIN_TOTP_SECRET:?Falta ADMIN_TOTP_SECRET, el secreto Base32 de su segundo factor}"
JSON='Content-Type: application/json'
source "$(dirname "$0")/lib-mfa.sh"
EMAIL="iam05.prueba.$(date +%s)@medicalsys.demo"
FIRST="Primera clave $(openssl rand -hex 3) ok!"
SECOND="Segunda clave $(openssl rand -hex 3) ok!"

post() { # ruta, cuerpo JSON, [token] -> "HTTP <código> <code> · <detalle>"
  local response
  response="$(curl -sS -w '\n%{http_code}' -X POST "$B$1" -H "$JSON" ${3:+-H "Authorization: Bearer $3"} -d "$2")"
  printf 'HTTP %s  %s\n' "$(tail -n1 <<<"$response")" \
    "$(sed '$d' <<<"$response" | jq -r 'if .accessToken then "acceso concedido" elif .mfaToken then "\(.status) · contraseña aceptada, falta el segundo factor" elif .status == "password-updated" then "contraseña actualizada" elif .temporaryPassword then "temporal emitida para \(.userCode)" else "\(.code // "-") · \(.detail // .title // "")" end')"
}
login_body() { jq -n --arg u "$USERNAME" --arg p "$1" '{username: $u, password: $p}'; }
change_body() { jq -n --arg u "$USERNAME" --arg c "$1" --arg p "$2" '{username: $u, currentPassword: $c, newPassword: $p}'; }

admin="$(mfa_login "$ADMIN_USERNAME" "$ADMIN_PASSWORD" "$ADMIN_TOTP_SECRET")"
[ -n "$admin" ] || { echo "No se pudo iniciar sesión como $ADMIN_USERNAME" >&2; exit 1; }
created="$(curl -sS -X POST "$B/users" -H "Authorization: Bearer $admin" -H "$JSON" \
  -d "$(jq -n --arg e "$EMAIL" '{email: $e, fullName: "Prueba IAM-05", activateImmediately: true, roles: [{role: "RECEPTIONIST"}]}')")"
user_id="$(jq -r .publicId <<<"$created")"
USERNAME="$(jq -r .username <<<"$created")"
echo "Cuenta $(jq -r .userCode <<<"$created") ($USERNAME)"
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

echo; echo "== 5. Segundo factor (IAM-07): QR en el primer ingreso, código en los siguientes"
read -r totp_secret access <<<"$(mfa_enroll "$USERNAME" "$SECOND")"
echo "   QR registrado y primer código: $([ -n "$access" ] && echo "acceso concedido")"
echo "   Ingreso siguiente:            $([ -n "$(mfa_login "$USERNAME" "$SECOND" "$totp_secret")" ] && echo "acceso concedido con el código")"
reset="$(curl -sS -X POST "$B/users/$user_id/reset-password" -H "Authorization: Bearer $admin")"
temporary="$(jq -r .temporaryPassword <<<"$reset")"
THIRD="Tercera clave $(openssl rand -hex 3) ok!"
post /auth/change-password "$(change_body "$temporary" "$THIRD")" >/dev/null
echo "   Tras otro reinicio:           $(post /auth/login "$(login_body "$THIRD")")"
