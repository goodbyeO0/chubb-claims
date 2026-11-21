# Cloudflare preview and GitHub Actions

The review app is available at https://chubb.ciptasys.com. Cloudflare forwards HTTPS requests to the existing local app on `127.0.0.1:18080`. The computer must remain awake, with Docker Desktop and the Compose services running. This is a hosted demo using fictional accounts and data, not a production insurance service.

## Local tunnel

The named tunnel `chubb` has its own user service; it does not replace the existing portfolio tunnel. Its configuration and credentials are outside the repository in `/home/izhan/.cloudflared/`.

```sh
systemctl --user status cloudflared-chubb
systemctl --user restart cloudflared-chubb
journalctl --user -u cloudflared-chubb -n 50 --no-pager
# Stop public access:
systemctl --user stop cloudflared-chubb
```

The service starts with the user's systemd session. This does not guarantee availability after logout or a reboot before login. Docker Desktop and the application must also be started. No automatic production deployment is configured.

## Private settings

`.env.example` contains public local-development defaults. This computer's ignored `.env` contains a randomly generated database password, port 18080, `SESSION_COOKIE_SECURE=true` and `FORWARD_HEADERS_STRATEGY=framework`. Its permissions are 0600. The existing database role password was rotated as well; changing an environment variable alone would not update an existing PostgreSQL volume.

The tunnel connects to a loopback-bound origin. Forwarded headers are trusted for HTTPS cookie handling in this topology; do not expose that origin directly to untrusted clients. Both session and CSRF cookies are checked for Secure when integration tests target HTTPS.

Cloudflare `cert.pem` and tunnel JSON credentials remain outside Git and Docker build context. Do not copy them into the repository or GitHub Actions. CI requires no account secrets. The four documented demo logins intentionally remain available for reviewer workflows; use fictional data only. Keep `.env` and tunnel credentials in your own secure backup if needed, not in commits or CI artifacts.

## Continuous integration

`.github/workflows/ci.yml` runs on pushes, pull requests and manual dispatch:

1. Backend: Java 21, Maven verify with PostgreSQL/Kafka Testcontainers, and contract validation.
2. Frontend: Node 24, locked npm install, production Angular build and fixture Playwright tests.
3. Integration, after both jobs pass: build the packaged app, start isolated PostgreSQL/Kafka, run real-API Playwright tests and both HTTP verification scripts, collect results and remove test volumes.

GitHub-hosted Ubuntu runners are used. Permissions are read-only, actions are pinned to commit SHAs, and artifacts expire after seven days. No runner or credentials from another project are reused. CI checks changes; it does not restart the laptop's public demo. To apply a reviewed update locally:

```sh
docker --context desktop-linux compose -p chubb-review up --build -d
curl --retry 60 --retry-all-errors --retry-delay 2 --retry-max-time 180 --max-time 5 -fsS https://chubb.ciptasys.com/actuator/health
```

Tests against the public URL create fictional claims. For isolated checks, use CI instead. To test the tunnel itself:

```sh
(cd frontend && E2E_BASE_URL=https://chubb.ciptasys.com npm run test:e2e)
```

The secret audit uses Gitleaks with redacted output. `.gitleaksignore` excludes only the exact historical fixture localStorage-key false positive, not a file or a credential category.
