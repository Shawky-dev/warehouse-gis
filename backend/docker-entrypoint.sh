#!/bin/sh
set -eu

APP_USER="${APP_USER:-app}"
APP_SECRETS_DIR="${APP_SECRETS_DIR:-/tmp/app-secrets}"

mkdir -p "${APP_SECRETS_DIR}"
chown "${APP_USER}:${APP_USER}" "${APP_SECRETS_DIR}"
chmod 700 "${APP_SECRETS_DIR}"

copy_file_resource_for_app() {
  resource_value="$1"
  target_name="$2"

  case "${resource_value}" in
    file:*)
      source_path="${resource_value#file:}"
      if [ ! -f "${source_path}" ]; then
        echo "Missing resource file: ${source_path}" >&2
        exit 1
      fi

      target_path="${APP_SECRETS_DIR}/${target_name}"
      cp "${source_path}" "${target_path}"
      chown "${APP_USER}:${APP_USER}" "${target_path}"
      chmod 400 "${target_path}"
      printf 'file:%s' "${target_path}"
      ;;
    *)
      printf '%s' "${resource_value}"
      ;;
  esac
}

if [ -n "${SPRING_DATASOURCE_PASSWORD_FILE:-}" ]; then
  if [ ! -f "${SPRING_DATASOURCE_PASSWORD_FILE}" ]; then
    echo "Missing datasource password file: ${SPRING_DATASOURCE_PASSWORD_FILE}" >&2
    exit 1
  fi

  export SPRING_DATASOURCE_PASSWORD="$(cat "${SPRING_DATASOURCE_PASSWORD_FILE}")"
fi

if [ -n "${SECURITY_JWT_PRIVATE_KEY:-}" ]; then
  export SECURITY_JWT_PRIVATE_KEY="$(copy_file_resource_for_app "${SECURITY_JWT_PRIVATE_KEY}" "jwt-private.pem")"
fi

if [ -n "${SECURITY_JWT_PUBLIC_KEY:-}" ]; then
  export SECURITY_JWT_PUBLIC_KEY="$(copy_file_resource_for_app "${SECURITY_JWT_PUBLIC_KEY}" "jwt-public.pem")"
fi

exec runuser -u "${APP_USER}" --preserve-environment -- sh -c 'exec java ${JAVA_OPTS:-} -jar /app/app.jar'
