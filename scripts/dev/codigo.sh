#!/usr/bin/env bash
# Solo desarrollo local: imprime el codigo de 6 digitos del segundo factor de una cuenta, con el secreto
# guardado en cuentas-locales.local.md (o OSI_TOTP_SECRET del .env para el OSI). Equivale a Google
# Authenticator, sin telefono. El codigo cambia cada 30 segundos y cada uno sirve una sola vez.
#
#   bash scripts/dev/codigo.sh administrador.medicalsys
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
user="${1:?Uso: codigo.sh <usuario>}"
secret="$(grep -E "^\| $user \|" "$ROOT/cuentas-locales.local.md" 2>/dev/null | awk -F'|' '{print $6}' | tr -d ' `' || true)"
if [ -z "$secret" ] || [ "$secret" = "OSI_TOTP_SECRETdel.env" ]; then
  secret="$(grep -E '^OSI_TOTP_SECRET=' "$ROOT/.env" | cut -d= -f2- || true)"
  [ "$user" = "$(grep -E '^OSI_USERNAME=' "$ROOT/.env" | cut -d= -f2- || echo oficial.seguridad.i)" ] || [ "$user" = oficial.seguridad.i ] \
    || { echo "No hay secreto para $user: corre scripts/dev/preparar-cuentas-locales.sh" >&2; exit 1; }
fi
# En Windows "python3" puede ser el alias de la tienda, que existe pero no ejecuta nada.
if python3 -c "" 2>/dev/null; then PY=python3; else PY=python; fi
"$PY" - "$secret" <<'EOF'
import base64, hashlib, hmac, struct, sys, time
secret = sys.argv[1].replace(' ', '').upper()
key = base64.b32decode(secret + '=' * (-len(secret) % 8))
step = int(time.time()) // 30
digest = hmac.new(key, struct.pack('>Q', step), hashlib.sha1).digest()
offset = digest[-1] & 0x0F
code = (struct.unpack('>I', digest[offset:offset + 4])[0] & 0x7FFFFFFF) % 1_000_000
print(f'{code:06d}  (vence en {30 - int(time.time()) % 30} s)')
EOF
