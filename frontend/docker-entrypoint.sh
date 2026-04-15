#!/bin/sh
set -eu

: "${PORT:=8080}"
: "${BACKEND_UPSTREAM:=http://backend.railway.internal:8080}"

envsubst '${PORT} ${BACKEND_UPSTREAM}' \
  < /etc/nginx/templates/default.conf.template \
  > /etc/nginx/conf.d/default.conf

exec nginx -g 'daemon off;'
