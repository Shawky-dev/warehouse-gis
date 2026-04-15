#!/usr/bin/env bash
# deploy.sh — Full Railway redeploy from project root
# Usage:
#   ./scripts/deploy.sh            # deploy both
#   ./scripts/deploy.sh backend    # backend only
#   ./scripts/deploy.sh frontend   # frontend only

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="${1:-all}"

ok()  { echo -e "\033[32m✓\033[0m $*"; }
err() { echo -e "\033[31m✗\033[0m $*" >&2; exit 1; }
hdr() { echo -e "\n\033[1;34m═══ $* ═══\033[0m"; }

# ── verify Railway CLI linked ─────────────────────────────────────────────────
if ! railway status &>/dev/null; then
  err "Not linked to a Railway project. Run: railway link"
fi

deploy_backend() {
  hdr "BACKEND — build"
  cd "$ROOT/backend"
  ./mvnw package -DskipTests -q || err "Maven build failed"
  ok "JAR built: $(ls target/*.jar)"

  hdr "BACKEND — deploy"
  # Stage into temp dir so gitignore doesn't block target/
  TMPDIR="$(mktemp -d)"
  trap 'rm -rf "$TMPDIR"' EXIT
  cp -r \
    Dockerfile.railway \
    railway.toml \
    docker-entrypoint.sh \
    .dockerignore \
    target \
    "$TMPDIR/"
  cd "$TMPDIR"
  railway up . --service backend --path-as-root
  cd "$ROOT"
  ok "Backend deployed"
}

deploy_frontend() {
  hdr "FRONTEND — build"
  cd "$ROOT/frontend"
  NODE_OPTIONS='--max-old-space-size=4096' npm run build || err "npm build failed"
  ok "Frontend built: $(du -sh dist | cut -f1) in dist/"

  hdr "FRONTEND — deploy"
  # Stage into temp dir so gitignore doesn't block dist/
  TMPDIR="$(mktemp -d)"
  trap 'rm -rf "$TMPDIR"' EXIT
  cp -r \
    Dockerfile.railway \
    railway.toml \
    nginx.conf.template \
    docker-entrypoint.sh \
    .dockerignore \
    dist \
    "$TMPDIR/"
  cd "$TMPDIR"
  railway up . --service frontend --path-as-root
  cd "$ROOT"
  ok "Frontend deployed"
}

case "$TARGET" in
  backend)
    deploy_backend
    ;;
  frontend)
    deploy_frontend
    ;;
  all)
    deploy_backend
    deploy_frontend
    ;;
  *)
    err "Unknown target '$TARGET'. Use: backend | frontend | all"
    ;;
esac

hdr "DONE"
echo "Frontend: https://frontend-production-55a5.up.railway.app"
echo "Backend:  https://backend-production-74b0.up.railway.app"
echo ""
echo "Logs:"
echo "  railway logs --service backend  --latest"
echo "  railway logs --service frontend --latest"
