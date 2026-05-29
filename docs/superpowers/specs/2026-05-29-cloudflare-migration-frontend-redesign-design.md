# SixEyes — Cloudflare Migration + Frontend Redesign

**Date:** 2026-05-29
**Status:** Approved (pending spec review)
**Branch:** `demonstration`

## Goal

Two changes:

1. **Deploy migration** — drop Google Cloud Platform (Cloud Run + Artifact Registry) and
   ngrok. Serve the whole app through a single Cloudflare Tunnel on the user's own domain
   `sixeyes.alcaria.dev`, path-based routing (no subdomains).
2. **Frontend redesign** — full rebuild of the React dashboard on shadcn/ui + Tailwind 4
   with the tweakcn theme `cml5dyjea000004jr66ytcg0l`, improved UX.

Backend changes are permitted where needed to support these goals.

---

## Part 1 — Deploy: GCP → Cloudflare Tunnel

### Routing decision

Single origin, path-based (user requirement):

- `sixeyes.alcaria.dev`          → frontend (React SPA)
- `sixeyes.alcaria.dev/api/v1/*` → Java middleware

nginx (the existing frontend container) serves the React build at `/` and reverse-proxies
`/api/v1/*` to the Java backend. One Cloudflare Tunnel exposes nginx to the internet.
Same-origin → no CORS complexity, JWT/headers trivial.

```
Browser → sixeyes.alcaria.dev → Cloudflare edge → cloudflared (local container)
            → nginx :80
                ├─ /          → React static build
                └─ /api/v1/*  → sixeyes-java:9090 → sixeyes-python:9999 → postgres
```

Python LibTorrent agent and Postgres stay internal on the docker network — never exposed
to the internet.

### Remove

- `.github/workflows/JavaPipeLine.yml` — GCP Cloud Run deploy stages.
- `.github/workflows/frontend-ci-cd-pipeline.yml` — GCP Cloud Run deploy stages.
- All GCP references: `GCP_PROJECT_ID`, `GCP_SA_KEY`, Artifact Registry, `gcloud run deploy`,
  `southamerica-east1`, deployment tags.
- `ngrok-db` and any ngrok service from `docker-compose.yml`; `NGROK_AUTHTOKEN`.
- GCP-specific secrets/env (`BACKEND_API_URL`, `PYTHON_SERVICE_URL` remote URL, etc.).

### Add

- **`cloudflared` service** in `docker-compose.yml`:
  - image `cloudflare/cloudflared:latest`, `command: tunnel run`, restart unless-stopped.
  - `TUNNEL_TOKEN=${CF_TUNNEL_TOKEN}` env (token-based named tunnel — no config file needed,
    ingress configured in the Cloudflare dashboard) OR a mounted `config.yml` + credentials
    file for ingress-as-code. **Decision: token-based** (simpler, secret stays in `.env`).
  - depends_on `frontend`.
  - on `sixeyes-net`.
- Cloudflare dashboard / DNS (manual, documented in README):
  - Named tunnel, public hostname `sixeyes.alcaria.dev` → service `http://sixeyes-frontend:80`.
  - DNS CNAME `sixeyes.alcaria.dev` → `<tunnel-id>.cfargotunnel.com`, proxied (orange cloud).

### nginx consolidation

Today there are **two** conflicting confs:
- `js/nginx.conf` — listens `5173`, proxies `/public/` (stale).
- `js/torrent-dashboard/nginx.conf` — listens `80`, no API proxy.
- `js/Dockerfile` `EXPOSE 5173`, but compose maps `4000:80`.

Consolidate to **one** conf used by `js/Dockerfile`, listening on `80`:

```nginx
server {
    listen 80;
    server_name _;
    root /usr/share/nginx/html;
    index index.html;

    gzip on;
    gzip_types text/plain text/css application/json application/javascript image/svg+xml;

    # API → Java middleware
    location /api/v1/ {
        proxy_pass         http://sixeyes-java:9090;
        proxy_http_version 1.1;
        proxy_set_header   Host              $host;
        proxy_set_header   X-Real-IP         $remote_addr;
        proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;
        proxy_read_timeout 60s;
    }

    # Cache hashed assets
    location ~* \.(js|css|png|svg|ico|woff2?)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # SPA fallback
    location / {
        try_files $uri $uri/ /index.html;
        add_header Cache-Control "no-cache";
    }

    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
}
```

Delete the redundant conf. `js/Dockerfile` `EXPOSE 80`. compose maps `4000:80` (local dev)
— cloudflared targets `sixeyes-frontend:80` directly on the docker network.

### Backend route versioning

All API routes move under `/api/v1/`. Implement with Spring context-path so it is one change
point:

`application.yml`:
```yaml
server:
  port: 9090
  servlet:
    context-path: /api/v1
```

Then trim controller `@RequestMapping` values so final paths are correct (context-path is
prepended automatically):

| Controller | Before | After (`@RequestMapping`) | Final path |
|---|---|---|---|
| AuthController | `/auth` | `/auth` | `/api/v1/auth/login` |
| TorrentController | `/api/torrents` | `/torrents` | `/api/v1/torrents/*` |
| SystemInfoController | `/api/system` | `/system` | `/api/v1/system/*` |
| SettingsController | `/api/settings` | `/settings` | `/api/v1/settings` |

Update accordingly:
- `SecurityConfig` / `JwtFilter` path matchers (e.g. permit `/auth/login` relative to
  context-path; verify matcher semantics with context-path active).
- `CorsConfig` — lock allowed origin to `https://sixeyes.alcaria.dev` (+ `http://localhost:4000`
  for local dev). Same-origin in prod, so CORS mainly matters for dev.
- Docker healthchecks: java `http://localhost:9090/api/v1/torrents/test`.
- `docker-compose.yml` `PYTHON_SERVICE_URL` stays internal `http://sixeyes-python:9999`.

### CI/CD (replace, do not deploy)

Both workflows become **build + test only** (no cloud deploy — deploy is `git pull` +
`docker compose up -d --build` on the local host):

- **Java workflow:** checkout → JDK 21 → `mvn clean test` → (PR) publish test results.
- **Frontend workflow:** checkout → Node 22 → `npm ci` → `npm run lint` → `npm run build`
  → verify `dist/`.
- Triggers: push/PR to `dev` (and/or `master`) — keep simple. Remove merge-to-main auto-merge
  and all deploy/tag jobs.

### New `.env` keys

```
CF_TUNNEL_TOKEN=<cloudflare named tunnel token>
DOMAIN=sixeyes.alcaria.dev
```
Remove `NGROK_AUTHTOKEN` and GCP-related keys. `.env.example` updated to match.

---

## Part 2 — Frontend: full redesign (shadcn + Tailwind 4)

### Stack

- Vite + React 18 + TypeScript (existing).
- **Tailwind 4** via `@tailwindcss/vite` plugin. `index.css` uses `@import "tailwindcss";`
  + `@custom-variant dark` + the full `:root` / `.dark` token block + `@theme inline` from
  tweakcn theme **`cml5dyjea000004jr66ytcg0l`**.
- Fonts: **Bricolage Grotesque** (sans) + **JetBrains Mono** (mono) — load via `index.html`
  Google Fonts link. Radius `1.25rem`. Light + dark modes both defined.
- **shadcn/ui** (new-york style). Add `components.json`, `@/*` path alias in `tsconfig.json`,
  `tsconfig.app.json`, and `vite.config.ts` (`resolve.alias`). Add deps:
  `class-variance-authority`, `clsx`, `tailwind-merge`, `lucide-react`, `@radix-ui/*`
  (pulled by shadcn add), `sonner`, `next-themes`-style theme provider (or a small custom
  `ThemeProvider` toggling `.dark` class). `recharts` already present.
- Install theme via: `npx shadcn@latest add https://tweakcn.com/r/themes/cml5dyjea000004jr66ytcg0l`
  then add components (`button card dialog input table tabs badge progress sonner chart
  sidebar sheet dropdown-menu tooltip skeleton form label select` etc.).

### Reuse (data layer unchanged in behavior)

- `services/api.ts` — change `BASE_URL` to relative `'/api/v1'` (same-origin); update endpoint
  paths to drop the now-redundant `/api` prefix segment (e.g. `/torrents/get`, `/auth/login`,
  `/system/info`, `/settings`). Keep token storage / 401 handling.
- `hooks/` — `useAuth`, `useTorrents`, `useSettings`, `useSystemMonitoring` (polling for live
  data). Keep logic; adjust as redesign requires.
- `types/` — keep, extend if new endpoints added.

### App shell

- shadcn **Sidebar** layout: nav (Dashboard, Torrents, Completed, Settings) + topbar with
  app title, dark/light **theme toggle**, logout.
- **Mobile responsive**: sidebar collapses to a `Sheet` drawer on small screens; tables become
  stacked cards.
- **Sonner** toasts for action feedback (add/pause/resume/delete, errors).

### Pages

1. **Dashboard** (overview)
   - Stat Cards: active / seeding / completed counts, total ↓ and ↑ speed.
   - **Chart** (shadcn chart wrapping recharts): network speed over time (↓/↑), storage/disk
     usage (per disk from `/system/disks`).
   - System health card (storage total/used/available from `/system/info`).
2. **Torrents**
   - Desktop: shadcn **Table** (title, size, progress, ↓/↑ speed, peers, ETA, status Badge,
     actions). Mobile: card list.
   - **Live progress**: `Progress` bars + speeds/ETA/peers, refreshed by polling hook.
     Optimistic UI on pause/resume/delete with rollback on error.
   - **Add-torrent Dialog**: tabbed — magnet paste **and** drag-drop `.torrent` file upload;
     save-path / drive picker (drives from system info / settings); inline validation.
3. **Completed** — Table of finished torrents (title, size, completedAt).
4. **Settings** — download-path form (shadcn Form + Input + Button), validation, toast on save.
5. **Login** — centered Card, JWT via `authApi.login`, error feedback.

### Backend support for `.torrent` upload (permitted change)

If the current add flow is magnet-only (`TorrentController.add` takes `{ magnet }`), add a
multipart endpoint to accept a `.torrent` file:

- `POST /api/v1/torrents/addFile` — `multipart/form-data`, field `file` (the `.torrent`),
  optional `savePath`. Java forwards file bytes to the Python agent
  (`POST sixeyes-python:9999/...`), which parses and starts the download via LibTorrent.
- Python agent: add a route to accept `.torrent` bytes (if not already present) alongside the
  existing magnet flow.
- Frontend `torrentApi.addFile(file, savePath)` uses `FormData` (no JSON `Content-Type`).
- If backend file support proves out of scope at build time, the dialog still supports magnet
  paste; file upload is the only piece gated on backend work.

### UX priorities (all four requested)

- **Live download progress** — progress bars, speed, ETA, peers; optimistic actions.
- **Dashboard overview** — counts + speed/storage charts + health cards.
- **Better add-torrent flow** — magnet + drag-drop file + save-path picker + validation.
- **Mobile responsive + dark toggle** — phone layout + light/dark switch (theme's two modes).

---

## Out of scope

- No rewrite of torrent business logic / LibTorrent agent internals.
- No new torrent features beyond what endpoints expose (no scheduling, RSS, categories, etc.).
- No multi-user / account system — single admin login stays.
- Cloudflare Access / Zero-Trust auth in front of the tunnel — optional future hardening,
  not in this scope (JWT remains the auth layer).

## Verification

- Local: `docker compose up -d --build` → `http://localhost:4000` serves redesigned UI;
  `http://localhost:4000/api/v1/torrents/test` returns `{status: UP}` through nginx proxy.
- Tunnel: `sixeyes.alcaria.dev` loads UI; `sixeyes.alcaria.dev/api/v1/torrents/test` works;
  login + list + add (magnet) + pause/resume/delete functional end-to-end.
- CI: Java `mvn test` green, frontend `npm run build` produces `dist/`.
- No GCP / ngrok references remain in repo (`grep -ri "gcloud\|cloud run\|ngrok\|gcp" `).
