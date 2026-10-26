#!/usr/bin/env bash
# Despliegue al VPS desde esta computadora, sin pasar por GitHub: construye las imagenes con el codigo
# tal como esta en disco (aunque no este commiteado), las sube por SSH y levanta los servicios.
#
#   bash scripts/deploy/deploy-local.sh ms-identidad ms-gateway   # solo esos microservicios
#   bash scripts/deploy/deploy-local.sh todos                      # los 14 microservicios
#   bash scripts/deploy/deploy-local.sh --frontend                 # solo el frontend (../medicalsys_frontend)
#   bash scripts/deploy/deploy-local.sh --levantar                 # no construye: arranca lo que este caido
#
# Variables (con sus valores por defecto):
#   VPS_HOST=98.91.235.248  VPS_USER=deploy  VPS_KEY=~/.ssh/los5furiosos-deploy
#   FRONTEND_DIR=../medicalsys_frontend
#
# La llave de deploy tiene frase de paso: cargala una vez en el agente y el script no la vuelve a pedir
# (ver docs/despliegue-vps.md, «Despliegue desde la computadora»).
#
# Las imagenes se suben con el mismo nombre y etiqueta que usa el VPS (IMAGE_REGISTRY e IMAGE_TAG de su
# .env), asi que el proximo despliegue de GitHub las reemplaza normalmente. El .env del VPS no se toca.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
VPS_HOST="${VPS_HOST:-98.91.235.248}"
VPS_USER="${VPS_USER:-deploy}"
VPS_KEY="${VPS_KEY:-$HOME/.ssh/los5furiosos-deploy}"
FRONTEND_DIR="${FRONTEND_DIR:-$ROOT/../medicalsys_frontend}"
DEPLOY_PATH=/opt/medicalsys
FRONTEND_PATH=/opt/medicalsys-frontend
ALL_SERVICES="ms-identidad ms-tenencia ms-catalogos ms-criptografia ms-pacientes ms-profesionales ms-agenda
  ms-historial ms-documental ms-facturacion ms-marketing ms-auditoria ms-notificaciones ms-gateway"
COMPOSE="docker compose -f compose.yaml -f compose.prod.yaml"

# En Windows, el ssh de OpenSSH del sistema usa el agente de Windows, que guarda la llave entre reinicios.
SSH=ssh; SSH_ADD=ssh-add
if [ -x /c/Windows/System32/OpenSSH/ssh.exe ]; then
  SSH=/c/Windows/System32/OpenSSH/ssh.exe; SSH_ADD=/c/Windows/System32/OpenSSH/ssh-add.exe
fi
ssh_opts=(-o ServerAliveInterval=30 -o StrictHostKeyChecking=accept-new)
# Si el agente ya tiene la llave no hace falta el archivo; si no, ssh pedira la frase de paso.
if ! "$SSH_ADD" -l >/dev/null 2>&1 && [ -f "$VPS_KEY" ]; then
  key="$VPS_KEY"; command -v cygpath >/dev/null && [ "$SSH" != ssh ] && key="$(cygpath -w "$VPS_KEY")"
  ssh_opts+=(-i "$key")
fi
vps() { "$SSH" "${ssh_opts[@]}" "$VPS_USER@$VPS_HOST" "$@"; }

services=(); frontend=false; only_up=false
for arg in "$@"; do
  case "$arg" in
    todos) read -r -a services <<<"$(echo $ALL_SERVICES)" ;;
    --frontend) frontend=true ;;
    --levantar) only_up=true ;;
    ms-*) [ -d "services/$arg" ] || { echo "No existe services/$arg" >&2; exit 2; }; services+=("$arg") ;;
    *) echo "Argumento desconocido: $arg" >&2; exit 2 ;;
  esac
done
if [ ${#services[@]} -eq 0 ] && [ "$frontend" = false ] && [ "$only_up" = false ]; then
  sed -n '2,9p' "$0" | sed 's/^# \{0,1\}//'; exit 2
fi

echo "== Conexion con $VPS_USER@$VPS_HOST"
vps "docker info --format 'Docker {{.ServerVersion}} en el VPS' && test -f $DEPLOY_PATH/.env"
[ -n "$(git status --porcelain -- services compose.yaml compose.prod.yaml infra 2>/dev/null)" ] \
  && echo "   (hay cambios sin commitear: se despliegan tal como estan en disco)"

# Arranca lo que haga falta en el VPS sin depender de esta conexion: si SSH se corta, sigue igual.
start_backend() {
  echo "== Levantando el backend en el VPS"
  vps "cd $DEPLOY_PATH && nohup $COMPOSE up -d --no-build --pull never --remove-orphans > /tmp/deploy-local.log 2>&1 < /dev/null &"
  for i in $(seq 1 60); do
    sleep 10
    status="$(vps "cd $DEPLOY_PATH && docker inspect --format '{{.State.Health.Status}}' \$($COMPOSE ps -q ms-gateway) 2>/dev/null" || true)"
    pending="$(vps "cd $DEPLOY_PATH && pgrep -f 'docker compose -f compose.yaml' >/dev/null && echo si || echo no")"
    printf '   %3ss gateway=%s compose_en_curso=%s\n' "$((i * 10))" "${status:-?}" "$pending"
    [ "$status" = healthy ] && [ "$pending" = no ] && break
  done
  vps "cd $DEPLOY_PATH && $COMPOSE ps --format '{{.Service}}  {{.Status}}'" || true
  vps "tail -3 /tmp/deploy-local.log" || true
}

if [ ${#services[@]} -gt 0 ]; then
  registry="$(vps "grep -E '^IMAGE_REGISTRY=' $DEPLOY_PATH/.env | cut -d= -f2-")"
  tag="$(vps "grep -E '^IMAGE_TAG=' $DEPLOY_PATH/.env | cut -d= -f2-")"
  [ -n "$registry" ] && [ -n "$tag" ] || { echo "El .env del VPS no tiene IMAGE_REGISTRY/IMAGE_TAG" >&2; exit 1; }
  images=()
  for svc in "${services[@]}"; do
    echo "== Construyendo $svc"
    docker build -q -t "$registry/$svc:$tag" "services/$svc" >/dev/null
    images+=("$registry/$svc:$tag")
  done
  echo "== Subiendo ${#images[@]} imagenes al VPS (puede tardar unos minutos)"
  docker save "${images[@]}" | gzip -1 | vps "gunzip | docker load"
  echo "== Copiando compose e infra"
  tar czf - compose.yaml compose.prod.yaml infra | vps "rm -rf $DEPLOY_PATH/infra && tar xzf - -C $DEPLOY_PATH"
fi

if [ ${#services[@]} -gt 0 ] || [ "$only_up" = true ]; then
  start_backend
fi

if [ "$frontend" = true ]; then
  [ -d "$FRONTEND_DIR" ] || { echo "No encuentro el frontend en $FRONTEND_DIR" >&2; exit 1; }
  image=ghcr.io/the-furious-five/medicalsys-frontend:latest
  echo "== Construyendo el frontend"
  docker build -q -t "$image" "$FRONTEND_DIR" >/dev/null
  echo "== Subiendo el frontend al VPS"
  docker save "$image" | gzip -1 | vps "gunzip | docker load"
  tar czf - -C "$FRONTEND_DIR/deploy" compose.yaml Caddyfile | vps "mkdir -p $FRONTEND_PATH && tar xzf - -C $FRONTEND_PATH"
  vps "cd $FRONTEND_PATH && docker compose up -d --pull never && (docker compose exec -T caddy caddy reload --config /etc/caddy/Caddyfile || true) && docker compose ps --format '{{.Service}}\t{{.Status}}'"
fi
echo "Listo."
