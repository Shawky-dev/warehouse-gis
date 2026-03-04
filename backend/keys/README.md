# JWT Key Material

Do not commit real private keys to the repository.

## Production (recommended)

Use Docker secrets and provide key files from outside git:

- `./secrets/jwt-private.pem`
- `./secrets/jwt-public.pem`

`docker-compose.yml` mounts them at runtime as:

- `/run/secrets/jwt_private_key`
- `/run/secrets/jwt_public_key`

## Generate / rotate keys

Run from the repository root:

```bash
mkdir -p secrets
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:4096 -out secrets/jwt-private.pem
openssl rsa -pubout -in secrets/jwt-private.pem -out secrets/jwt-public.pem
```

After rotating keys, update `SECURITY_JWT_KEY_ID` in `.env`.
