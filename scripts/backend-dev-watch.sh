#!/bin/sh
set -eu

APP_PID=""
POLL_INTERVAL="${BACKEND_WATCH_INTERVAL_SECONDS:-1}"

: "${SPRING_PROFILES_ACTIVE:=dev}"
: "${SPRING_DATASOURCE_URL:=jdbc:postgresql://localhost:5432/warehouse}"
: "${SPRING_DATASOURCE_USERNAME:=warehouse}"
: "${SPRING_DATASOURCE_PASSWORD:=warehouse}"
: "${SECURITY_CORS_ALLOWED_ORIGIN_PATTERNS:=http://localhost:*,http://127.0.0.1:*}"
: "${SECURITY_AUTH_REFRESH_COOKIE_NAME:=refresh_token}"
: "${SECURITY_AUTH_REFRESH_COOKIE_PATH:=/landlord/auth}"
: "${SECURITY_AUTH_REFRESH_COOKIE_SAME_SITE:=Lax}"
: "${SECURITY_AUTH_REFRESH_COOKIE_SECURE:=false}"
: "${SECURITY_AUTH_REFRESH_COOKIE_DOMAIN:=}"
: "${SECURITY_JWT_PRIVATE_KEY:=file:src/test/resources/keys/jwt-private.pem}"
: "${SECURITY_JWT_PUBLIC_KEY:=file:src/test/resources/keys/jwt-public.pem}"
: "${GEOSERVER_URL:=http://localhost:8600/geoserver}"

export SPRING_PROFILES_ACTIVE
export SPRING_DATASOURCE_URL
export SPRING_DATASOURCE_USERNAME
export SPRING_DATASOURCE_PASSWORD
export SECURITY_CORS_ALLOWED_ORIGIN_PATTERNS
export SECURITY_AUTH_REFRESH_COOKIE_NAME
export SECURITY_AUTH_REFRESH_COOKIE_PATH
export SECURITY_AUTH_REFRESH_COOKIE_SAME_SITE
export SECURITY_AUTH_REFRESH_COOKIE_SECURE
export SECURITY_AUTH_REFRESH_COOKIE_DOMAIN
export SECURITY_JWT_PRIVATE_KEY
export SECURITY_JWT_PUBLIC_KEY
export GEOSERVER_URL

if [ "${PWD##*/}" != "backend" ]; then
  if [ -d "./backend" ]; then
    cd ./backend
  fi
fi

fingerprint_backend() {
  {
    if [ -f pom.xml ]; then
      sha256sum pom.xml
    fi

    if [ -d src ]; then
      find src -type f | LC_ALL=C sort | while IFS= read -r file; do
        sha256sum "$file"
      done
    fi
  } | sha256sum | awk '{print $1}'
}

start_backend() {
  echo "Starting Spring Boot dev server..."
  ./mvnw -q -DskipTests spring-boot:run &
  APP_PID=$!
}

stop_backend() {
  if [ -n "${APP_PID}" ] && kill -0 "${APP_PID}" 2>/dev/null; then
    kill "${APP_PID}" 2>/dev/null || true
    wait "${APP_PID}" || true
  fi
  APP_PID=""
}

cleanup() {
  stop_backend
}

trap cleanup EXIT INT TERM

LAST_FINGERPRINT="$(fingerprint_backend)"
start_backend

while true; do
  sleep "${POLL_INTERVAL}"

  if [ -n "${APP_PID}" ] && ! kill -0 "${APP_PID}" 2>/dev/null; then
    echo "Spring Boot process exited; restarting..."
    LAST_FINGERPRINT="$(fingerprint_backend)"
    start_backend
    continue
  fi

  CURRENT_FINGERPRINT="$(fingerprint_backend)"
  if [ "${CURRENT_FINGERPRINT}" != "${LAST_FINGERPRINT}" ]; then
    LAST_FINGERPRINT="${CURRENT_FINGERPRINT}"
    echo "Detected backend change; restarting Spring Boot..."
    stop_backend
    start_backend
  fi
done
