# Cloudflare preview and GitHub Actions

The review app is available at https://claims.ciptasys.com. Cloudflare forwards HTTPS requests to the existing local app on `127.0.0.1:18080`. The computer must remain awake, with Docker Desktop and the Compose services running. This is a hosted demo using fictional accounts and data, not a production insurance service.

## Local tunnel

The application tunnel has its own user service; it does not replace the existing portfolio tunnel. Its configuration and credentials are outside the repository in `/home/izhan/.cloudflared/`.

```sh
systemctl --user status cloudflared-claims
systemctl --user restart cloudflared-claims
journalctl --user -u cloudflared-claims -n 50 --no-pager
# Stop public access:
systemctl --user stop cloudflared-claims
```

Automatic startup is configured on this laptop: Docker Desktop is enabled at graphical sign-in, `claims-app.service` waits for its daemon and starts the existing Compose image, and `cloudflared-claims.service` starts the tunnel. PostgreSQL, Kafka and the API have `unless-stopped` restart policies. No terminal commands are needed after logging in. The site is unavailable while the laptop is off or asleep, and startup before graphical login is not provided by Docker Desktop. Opening the lid resumes the existing services when the laptop wakes.

The startup service uses the existing image and does not pull or build on every login. Successful master pushes are deployed automatically by the separate timer described below. Nginx is not involved: Spring Boot serves both Angular and the API, and Cloudflare provides public HTTPS.

```sh
systemctl --user status docker-desktop claims-app cloudflared-claims
# Restart only Claims, preserving data:
systemctl --user restart claims-app
# Stop the app and public tunnel for this session:
systemctl --user stop cloudflared-claims claims-app
# Disable automatic startup of Claims (Docker Desktop remains enabled):
systemctl --user disable --now cloudflared-claims claims-app
```

The reproducible app unit and startup script are in `ops/`. This unit uses the laptop's current checkout path; adjust WorkingDirectory and ExecStart before installing elsewhere. Startup retries Docker readiness and health, and systemd retries failed starts. Installation on this same laptop:

```sh
mkdir -p ~/.config/systemd/user
cp ops/claims-app.service ~/.config/systemd/user/
systemctl --user daemon-reload
systemctl --user enable --now docker-desktop claims-app
```

Automatic deployment targets this review demo; it is not an always-on production host.

## Private settings

`.env.example` contains public local-development defaults. This computer's ignored `.env` contains a randomly generated database password, port 18080, `SESSION_COOKIE_SECURE=true` and `FORWARD_HEADERS_STRATEGY=framework`. Its permissions are 0600. The existing database role password was rotated as well; changing an environment variable alone would not update an existing PostgreSQL volume.

The tunnel connects to a loopback-bound origin. Forwarded headers are trusted for HTTPS cookie handling in this topology; do not expose that origin directly to untrusted clients. Both session and CSRF cookies are checked for Secure when integration tests target HTTPS.

Cloudflare `cert.pem` and tunnel JSON credentials remain outside Git and Docker build context. Do not copy them into the repository or GitHub Actions. CI requires no account secrets. The four documented demo logins intentionally remain available for reviewer workflows; use fictional data only. Keep `.env` and tunnel credentials in your own secure backup if needed, not in commits or CI artifacts.

## Continuous integration

`.github/workflows/ci.yml` runs on pushes, pull requests and manual dispatch:

1. Backend: Java 21, Maven verify with PostgreSQL/Kafka Testcontainers, and contract validation.
2. Frontend: Node 24, locked npm install, production Angular build and fixture Playwright tests.
3. Integration, after both jobs pass: build the packaged app, start isolated PostgreSQL/Kafka, run real-API Playwright tests and both HTTP verification scripts, collect results and remove test volumes.

GitHub-hosted Ubuntu runners are used. Permissions are read-only, actions are pinned to commit SHAs, and artifacts expire after seven days. No runner or credentials from another project are reused. After successful master-push integration tests, CI exports the exact tested Docker image as a seven-day artifact. A local service downloads and deploys that artifact automatically.

## Automatic deployment

Push to `master` → all CI jobs pass → tested image artifact → laptop deploys within roughly one to two minutes plus download/startup time. No manual rebuild is required. Pull requests, failed checks and other branches never deploy. A `[skip ci]` commit does not deploy; push normally or rerun the relevant push workflow when a deployment is wanted.

`claims-deploy.timer` runs every minute while your user session is running. It uses the existing local GitHub CLI login to read this repository's Actions metadata and artifacts. Credentials are not passed to containers or uploaded to GitHub. There is no self-hosted runner registered to this public repository and no inbound deployment port.

The installed controller is a trusted local copy in `~/.local/lib/claims-deploy/`; it does not execute scripts downloaded from the repository. It checks the exact latest master SHA and the successful push run, downloads its image, rechecks master before switching, and updates only the API container. Local Compose settings, `.env`, PostgreSQL/Kafka volumes and your source working tree remain intact. Changes to host infrastructure/Compose settings or the controller itself still require a deliberate local installation; automatic deployment covers the packaged frontend/backend image.

The previous image is retained. If startup health fails, the controller restores that image and marks the run attempt failed so it does not loop every minute. A rerun or new commit allows another attempt. Application rollback does not undo database migrations; the existing migration remains unchanged. Updates briefly interrupt the demo and end its in-memory login sessions. If the laptop is offline, deployment waits until it is online with Docker running. Artifacts expire after seven days; rerun the master push workflow if its artifact has expired.

```sh
# Check deployment and the exact deployed commit:
systemctl --user status claims-deploy.timer
journalctl --user -u claims-deploy.service -n 40 --no-pager
cat ~/.local/state/claims-deploy/state.json
# Check now, or pause/resume automatic deployment:
systemctl --user start claims-deploy.service
systemctl --user disable --now claims-deploy.timer
systemctl --user enable --now claims-deploy.timer
```

To install/update the trusted controller on this laptop:

```sh
mkdir -p ~/.local/lib/claims-deploy ~/.config/systemd/user
install -m 700 ops/auto-deploy.py ~/.local/lib/claims-deploy/auto-deploy.py
cp ops/claims-deploy.service ops/claims-deploy.timer ~/.config/systemd/user/
systemctl --user daemon-reload
systemctl --user enable --now claims-deploy.timer
```

GitHub's green CI status means the image is tested and ready. Local deployment success is recorded in the service journal and state file, since the laptop may be asleep when CI finishes. For a deliberate manual deployment instead:

```sh
docker --context desktop-linux compose -p claims-review up --build -d
curl --retry 60 --retry-all-errors --retry-delay 2 --retry-max-time 180 --max-time 5 -fsS https://claims.ciptasys.com/actuator/health
```

Tests against the public URL create fictional claims. For isolated checks, use CI instead. To test the tunnel itself:

```sh
(cd frontend && E2E_BASE_URL=https://claims.ciptasys.com npm run test:e2e)
```

The secret audit uses Gitleaks with redacted output. `.gitleaksignore` excludes only the exact historical fixture localStorage-key false positive, not a file or a credential category.

### Existing host compatibility

The installed user services may set `CLAIMS_COMPOSE_PROJECT` to retain an existing private Compose project and its volumes. New checkouts use `claims-review`. The deployment controller defaults to `~/Projects/claims-workbench`; `CLAIMS_ROOT` can override that path. Local service overrides are not committed.
