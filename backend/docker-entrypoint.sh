#!/bin/sh
set -eu

if [ -n "${SPRING_DATASOURCE_PASSWORD_FILE:-}" ]; then
  if [ ! -f "${SPRING_DATASOURCE_PASSWORD_FILE}" ]; then
    echo "Missing datasource password file: ${SPRING_DATASOURCE_PASSWORD_FILE}" >&2
    exit 1
  fi

  export SPRING_DATASOURCE_PASSWORD="$(cat "${SPRING_DATASOURCE_PASSWORD_FILE}")"
fi

exec java ${JAVA_OPTS:-} -jar /app/app.jar
