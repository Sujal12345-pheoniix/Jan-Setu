#!/usr/bin/env bash
# Generates an RS256 key pair for JWT signing and prints the two environment variables.
# Store the output in your secret manager (staging/production) or your local, git-ignored .env.
# Never commit generated keys.
set -euo pipefail

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$tmp/private.pem" 2>/dev/null
openssl rsa -in "$tmp/private.pem" -pubout -out "$tmp/public.pem" 2>/dev/null

# Single-line values with literal \n, accepted by the backend.
echo "JWT_PRIVATE_KEY=$(awk 'BEGIN{ORS="\\n"} {print}' "$tmp/private.pem")"
echo "JWT_PUBLIC_KEY=$(awk 'BEGIN{ORS="\\n"} {print}' "$tmp/public.pem")"
