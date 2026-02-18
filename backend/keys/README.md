# JWT Dev Keys

This folder stores local development RSA keys for JWT signing (`RS256`).

Files:
- `jwt-private.pem` (never commit)
- `jwt-public.pem`

Generate/rotate locally:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:4096 -out keys/jwt-private.pem
openssl rsa -pubout -in keys/jwt-private.pem -out keys/jwt-public.pem
```

After rotating keys, update `security.jwt.key-id` in `src/main/resources/application.yaml`.
In production, key material should come from a secure secret manager or KMS.
