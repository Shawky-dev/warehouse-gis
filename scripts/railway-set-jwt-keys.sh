#!/bin/sh
set -e
railway variable set -s backend SECURITY_JWT_PRIVATE_KEY --stdin < ./secrets/jwt-private.pem
railway variable set -s backend SECURITY_JWT_PUBLIC_KEY --stdin < ./secrets/jwt-public.pem
echo "JWT keys set."
