# Runtime Secrets

Create these files before starting the production stack:

- `secrets/postgres_password.txt`
- `secrets/jwt-private.pem`
- `secrets/jwt-public.pem`

Generate JWT keys:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:4096 -out secrets/jwt-private.pem
openssl rsa -pubout -in secrets/jwt-private.pem -out secrets/jwt-public.pem
```

Generate a strong DB password:

```bash
openssl rand -base64 48 > secrets/postgres_password.txt
```
