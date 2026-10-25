# Inicio de sesión con usuario, contraseña y segundo factor TOTP (IAM-07), para los scripts de prueba.
# Se carga con «source»; espera $B (…/api/v1/identity) y usa curl, jq y oathtool
# (Alpine: oath-toolkit-oathtool; Debian/Ubuntu: oathtool).
#
#   totp_code SECRETO              -> código de 6 dígitos que el servidor todavía no aceptó
#   mfa_login USUARIO CLAVE SECRETO -> accessToken (cuenta con el segundo factor ya registrado)
#   mfa_enroll USUARIO CLAVE        -> "SECRETO TOKEN" (primer ingreso: registra el QR y entra)
#
# El servidor no acepta dos veces un mismo paso de 30 s (ni uno anterior al último aceptado), así que
# totp_code recuerda el último paso que usó con cada secreto y, si hace falta, espera al siguiente.

# Base de ms-identidad: IDENTITY_BASE si el script usa $B para otra cosa.
MFA_JSON='Content-Type: application/json'

totp_code() {
  local secret="${1// /}" file now step last
  file="/tmp/medicalsys-totp-$(printf '%s' "$secret" | sha256sum | cut -c1-16)"
  now=$(( $(date +%s) / 30 ))
  last="$(cat "$file" 2>/dev/null || echo 0)"
  step=$now
  [ "$step" -le "$last" ] && step=$((last + 1))
  # El servidor acepta hasta un paso adelantado por el desfase de reloj; más allá, se espera.
  while [ "$step" -gt $(( $(date +%s) / 30 + 1 )) ]; do sleep 2; done
  printf '%s' "$step" > "$file"
  oathtool --totp -b -N "@$((step * 30))" "$secret"
}

# Primer paso: devuelve el cuerpo de la respuesta del login (202 con el desafío, o el error).
mfa_password_step() {
  curl -sS -X POST "${IDENTITY_BASE:-$B}/auth/login" -H "$MFA_JSON" \
    -d "$(jq -n --arg u "$1" --arg p "$2" '{username: $u, password: $p}')"
}

mfa_login() {
  local challenge token
  challenge="$(mfa_password_step "$1" "$2")"
  token="$(jq -r '.mfaToken // empty' <<<"$challenge")"
  [ -n "$token" ] || { echo "login de $1: $(jq -c '{status, code, detail}' <<<"$challenge")" >&2; return 1; }
  curl -sS -X POST "${IDENTITY_BASE:-$B}/auth/mfa/verify" -H "$MFA_JSON" \
    -d "$(jq -n --arg t "$token" --arg c "$(totp_code "$3")" '{mfaToken: $t, code: $c}')" | jq -r '.accessToken // empty'
}

mfa_enroll() {
  local challenge token secret access
  challenge="$(mfa_password_step "$1" "$2")"
  token="$(jq -r 'select(.status == "MFA_ENROLLMENT_REQUIRED") | .mfaToken' <<<"$challenge")"
  [ -n "$token" ] || { echo "login de $1: $(jq -c '{status, code, detail}' <<<"$challenge")" >&2; return 1; }
  secret="$(curl -sS -X POST "${IDENTITY_BASE:-$B}/auth/mfa/enrollment" -H "$MFA_JSON" \
    -d "$(jq -n --arg t "$token" '{mfaToken: $t}')" | jq -r '.secret // empty' | tr -d ' ')"
  [ -n "$secret" ] || { echo "no se pudo registrar el segundo factor de $1" >&2; return 1; }
  access="$(curl -sS -X POST "${IDENTITY_BASE:-$B}/auth/mfa/activate" -H "$MFA_JSON" \
    -d "$(jq -n --arg t "$token" --arg c "$(totp_code "$secret")" '{mfaToken: $t, code: $c}')" | jq -r '.accessToken // empty')"
  [ -n "$access" ] || { echo "el primer código de $1 no se aceptó" >&2; return 1; }
  printf '%s %s' "$secret" "$access"
}
