#!/usr/bin/env bash
# fresh-deploy.sh — Bootstrap complete deployment onto a fresh Railway account
#
# Run ONCE on a new Railway account. For subsequent updates use: ./scripts/deploy.sh
#
# Prerequisites:
#   - Railway CLI installed:  npm i -g @railway/cli
#   - Java 21 + Maven available
#   - Node.js 18+ available
#   - JWT PEM keys in ./secrets/jwt-private.pem and ./secrets/jwt-public.pem

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# ── helpers ───────────────────────────────────────────────────────────────────
ok()   { echo -e "\033[32m✓\033[0m $*"; }
warn() { echo -e "\033[33m⚠\033[0m  $*"; }
err()  { echo -e "\033[31m✗\033[0m $*" >&2; exit 1; }
hdr()  { echo -e "\n\033[1;34m════════════════════════════════════════\033[0m"; \
         echo -e "\033[1;34m  $*\033[0m"; \
         echo -e "\033[1;34m════════════════════════════════════════\033[0m"; }
ask()  { printf "  \033[33m?\033[0m %-45s " "$1"; read -r REPLY; echo "$REPLY"; }
askp() { printf "  \033[33m?\033[0m %-45s " "$1"; read -rs REPLY; echo; echo "$REPLY"; }

# ── step 0: prerequisites ─────────────────────────────────────────────────────
hdr "Step 0 — Checking prerequisites"

command -v railway &>/dev/null || err "Railway CLI not found. Install: npm i -g @railway/cli"
command -v java    &>/dev/null || err "Java not found. Install JDK 21."
command -v mvn &>/dev/null || [ -f "$ROOT/backend/mvnw" ] || err "Maven not found."
command -v node    &>/dev/null || err "Node.js not found."
command -v npm     &>/dev/null || err "npm not found."

[ -f "$ROOT/secrets/jwt-private.pem" ] || err "Missing: secrets/jwt-private.pem"
[ -f "$ROOT/secrets/jwt-public.pem"  ] || err "Missing: secrets/jwt-public.pem"

ok "All prerequisites found"

# ── step 1: collect config ────────────────────────────────────────────────────
hdr "Step 1 — Configuration"

echo "  Press ENTER to accept [defaults shown in brackets]"
echo ""

PROJECT_NAME=$(ask  "Railway project name [warehouse-gis-webapp]:")
PROJECT_NAME="${PROJECT_NAME:-warehouse-gis-webapp}"

DB_NAME=$(ask "PostgreSQL database name [warehouse]:")
DB_NAME="${DB_NAME:-warehouse}"

DB_USER=$(ask "PostgreSQL username [warehouse]:")
DB_USER="${DB_USER:-warehouse}"

DB_PASS=$(askp "PostgreSQL password (required):")
[ -z "$DB_PASS" ] && err "PostgreSQL password is required"

GS_ADMIN_PASS=$(askp "GeoServer admin password [geoserver]:")
GS_ADMIN_PASS="${GS_ADMIN_PASS:-geoserver}"

JWT_KEY_ID=$(ask "JWT Key ID [warehouse-k1]:")
JWT_KEY_ID="${JWT_KEY_ID:-warehouse-k1}"

JWT_ISSUER=$(ask "JWT Issuer [warehouse-platform-api]:")
JWT_ISSUER="${JWT_ISSUER:-warehouse-platform-api}"

JWT_AUDIENCE=$(ask "JWT Audience [warehouse-platform-web]:")
JWT_AUDIENCE="${JWT_AUDIENCE:-warehouse-platform-web}"

echo ""
ok "Config collected"

# Read JWT keys from files
JWT_PRIVATE="$(cat "$ROOT/secrets/jwt-private.pem")"
JWT_PUBLIC="$(cat "$ROOT/secrets/jwt-public.pem")"

# ── step 2: login + create project ───────────────────────────────────────────
hdr "Step 2 — Railway login & project creation"

railway login
railway init --name "$PROJECT_NAME"

ok "Logged in and project '$PROJECT_NAME' created"

# ── step 3: PostGIS ───────────────────────────────────────────────────────────
hdr "Step 3 — Deploy PostGIS"

echo "  When prompted 'No service found, create one?' — type: y"
echo ""

cd "$ROOT/postgis"
railway up . --service postgis --path-as-root

railway variable set -s postgis \
  "POSTGRES_DB=$DB_NAME" \
  "POSTGRES_USER=$DB_USER" \
  "POSTGRES_PASSWORD=$DB_PASS"

railway redeploy --service postgis --yes

ok "PostGIS deployed (private DNS: postgis.railway.internal:5432)"
warn "For data persistence, add a Volume at /var/lib/postgresql/data via the Railway dashboard"

# ── step 4: GeoServer ─────────────────────────────────────────────────────────
hdr "Step 4 — Deploy GeoServer"

echo "  When prompted 'No service found, create one?' — type: y"
echo ""

cd "$ROOT/geoserver"
railway up . --service geoserver --path-as-root

railway variable set -s geoserver \
  GEOSERVER_ADMIN_USER=admin \
  "GEOSERVER_ADMIN_PASSWORD=$GS_ADMIN_PASS" \
  GEOSERVER_DATA_DIR=/opt/geoserver_data \
  INITIAL_MEMORY=256M \
  MAXIMUM_MEMORY=384M

railway redeploy --service geoserver --yes

ok "GeoServer deployed (private DNS: geoserver.railway.internal:8080)"
warn "For data persistence, add a Volume at /opt/geoserver_data via the Railway dashboard"

# ── step 5: Backend ───────────────────────────────────────────────────────────
hdr "Step 5 — Build & deploy backend"

echo "  Building Spring Boot JAR (this takes ~3 minutes)..."
cd "$ROOT/backend"
./mvnw package -DskipTests -q || err "Maven build failed"
ok "JAR built: $(ls target/*.jar)"

echo ""
echo "  When prompted 'No service found, create one?' — type: y"
echo ""

railway variable set -s backend \
  "SPRING_DATASOURCE_URL=jdbc:postgresql://postgis.railway.internal:5432/$DB_NAME" \
  "SPRING_DATASOURCE_USERNAME=$DB_USER" \
  "SPRING_DATASOURCE_PASSWORD=$DB_PASS" \
  "JAVA_OPTS=-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport" \
  "GEOSERVER_URL=http://geoserver.railway.internal:8080/geoserver" \
  "GEOSERVER_ADMIN_USER=admin" \
  "GEOSERVER_ADMIN_PASSWORD=$GS_ADMIN_PASS" \
  "GEOSERVER_DB_HOST=postgis.railway.internal" \
  "GEOSERVER_DB_PORT=5432" \
  "GEOSERVER_DB_NAME=$DB_NAME" \
  "SECURITY_JWT_ISSUER=$JWT_ISSUER" \
  "SECURITY_JWT_AUDIENCE=$JWT_AUDIENCE" \
  "SECURITY_JWT_ACCESS_TOKEN_TTL=10m" \
  "SECURITY_JWT_REFRESH_TOKEN_TTL=7d" \
  "SECURITY_JWT_KEY_ID=$JWT_KEY_ID" \
  "SECURITY_AUTH_REFRESH_COOKIE_NAME=refresh_token" \
  "SECURITY_AUTH_REFRESH_COOKIE_PATH=/landlord/auth" \
  "SECURITY_AUTH_REFRESH_COOKIE_SAME_SITE=Lax" \
  "SECURITY_AUTH_REFRESH_COOKIE_SECURE=true" \
  "SERVER_FORWARD_HEADERS_STRATEGY=framework" \
  "SPRING_PROFILES_ACTIVE=railway"

# JWT keys as multiline values (set individually via file redirect)
railway variable set -s backend "SECURITY_JWT_PRIVATE_KEY=$JWT_PRIVATE"
railway variable set -s backend "SECURITY_JWT_PUBLIC_KEY=$JWT_PUBLIC"

railway up . --service backend --path-as-root

ok "Backend deployed"

hdr "Step 5b — Generate backend public domain"
BACKEND_DOMAIN_OUTPUT=$(railway domain --service backend 2>&1)
BACKEND_URL=$(echo "$BACKEND_DOMAIN_OUTPUT" | grep -o 'https://[^[:space:]]*' | head -1)
[ -z "$BACKEND_URL" ] && err "Could not get backend URL. Check: railway domain --service backend"
ok "Backend URL: $BACKEND_URL"

# ── step 6: Frontend ──────────────────────────────────────────────────────────
hdr "Step 6 — Build & deploy frontend"

echo "  Building frontend (this takes ~3 minutes, needs 4GB RAM)..."
cd "$ROOT/frontend"
NODE_OPTIONS='--max-old-space-size=4096' npm run build || err "Frontend build failed"
ok "Frontend built ($(du -sh dist | cut -f1))"

echo ""
echo "  When prompted 'No service found, create one?' — type: y"
echo ""

railway variable set -s frontend \
  "BACKEND_UPSTREAM=$BACKEND_URL"

railway up . --service frontend --path-as-root

ok "Frontend deployed"

hdr "Step 6b — Generate frontend public domain"
FRONTEND_DOMAIN_OUTPUT=$(railway domain --service frontend 2>&1)
FRONTEND_URL=$(echo "$FRONTEND_DOMAIN_OUTPUT" | grep -o 'https://[^[:space:]]*' | head -1)
[ -z "$FRONTEND_URL" ] && err "Could not get frontend URL. Check: railway domain --service frontend"
ok "Frontend URL: $FRONTEND_URL"

# ── step 7: cross-service wiring ──────────────────────────────────────────────
hdr "Step 7 — Cross-service wiring"

railway variable set -s backend \
  "SECURITY_CORS_ALLOWED_ORIGIN_PATTERNS=$FRONTEND_URL"

railway redeploy --service backend --yes
railway redeploy --service frontend --yes

ok "CORS configured. Backend redeploying."

# ── done ──────────────────────────────────────────────────────────────────────
hdr "DEPLOYMENT COMPLETE"

echo ""
echo "  ┌─────────────────────────────────────────────────────────┐"
echo "  │  Frontend :  $FRONTEND_URL"
echo "  │  Backend  :  $BACKEND_URL"
echo "  └─────────────────────────────────────────────────────────┘"
echo ""
warn "Backend cold-start takes ~20s after sleep. Open the site 30s before presenting."
echo ""
echo "  Useful commands:"
echo "    railway logs --service backend  --latest"
echo "    railway logs --service frontend --latest"
echo "    railway logs --service postgis  --latest"
echo "    railway logs --service geoserver --latest"
echo ""
echo "  To redeploy after code changes:"
echo "    ./scripts/deploy.sh            # both"
echo "    ./scripts/deploy.sh backend    # backend only"
echo "    ./scripts/deploy.sh frontend   # frontend only"
echo ""
warn "IMPORTANT — Add Railway Volumes for data persistence (via dashboard):"
echo "    postgis  → /var/lib/postgresql/data"
echo "    geoserver → /opt/geoserver_data"
