# SixEyes 🚀
### A hybrid torrent manager built on Java, Python, React

Manage your torrents remotely from anywhere — no VPN, no port forwarding, no static IP. The whole app runs locally in Docker and is published on your own domain through a single **Cloudflare Tunnel**. The browser talks to a React frontend; an nginx reverse proxy forwards `/api/v1/*` to the Spring Boot middleware, which drives a local Python agent running LibTorrent.

**Simple idea. Complex engineering.**

---

## 🧱 Architecture

Everything runs locally via `docker compose`. Only the frontend container is exposed to the internet — through one Cloudflare Tunnel, on `sixeyes.alcaria.dev`. The Python agent and PostgreSQL stay internal on the docker network.

```
Browser ── https://sixeyes.alcaria.dev ──► Cloudflare edge
                                              │
                                              ▼ (cloudflared, outbound-only)
                                        ┌──────────────┐
                                        │ nginx (:80)  │  frontend container
                                        │  /        → React SPA
                                        │  /api/v1/ → java:9090
                                        └──────┬───────┘
                                               ▼
                                        ┌──────────────┐
                                        │ Spring Boot  │  middleware (:9090)
                                        │  JWT, formats torrent data
                                        └──────┬───────┘
                                               ▼ (internal docker network)
                              ┌────────────────┴───────────────┐
                              ▼                                 ▼
                     ┌──────────────┐                  ┌──────────────────┐
                     │ Python Agent │                  │  PostgreSQL 16   │
                     │ FastAPI +    │                  │  torrent state   │
                     │ LibTorrent   │                  │  (internal)      │
                     └──────────────┘                  └──────────────────┘
```

### ⚙️ Middleware — Spring Boot (Java 21)
- JWT-authenticated REST API, all routes under `/api/v1`
- Orchestrates the local Python agent over the internal docker network
- Formats raw torrent data (speeds, progress, status), persists to PostgreSQL
- CORS locked to `sixeyes.alcaria.dev` (same-origin in production)

### 🎨 Frontend — React + TypeScript
- JWT login, real-time dashboard (5-second polling)
- Live download/upload charts, host disk monitoring, completed downloads, settings
- Served as static files by nginx, which also reverse-proxies the API (same origin → no CORS)

### 🐍 Local Agent — Python + FastAPI
- LibTorrent operations (add magnet, add `.torrent` file, pause, resume, remove)
- Host filesystem / disk usage reporting
- **Internal only** — reachable solely by the Java middleware, never published to the host or internet
- Runs as a non-root user; validates download paths and enforces file/size caps

### 💾 Database — PostgreSQL 16
- Runs in Docker, internal to the compose network, persists torrent state

**The bridge:** one Cloudflare Tunnel replaces both the old ngrok tunnels and Google Cloud Run. `cloudflared` makes an outbound connection to Cloudflare, so no inbound ports are opened on your machine.

---

## 🌐 Tech Stack

| Layer | Technology |
|-------|------------|
| **Frontend** | React 18, TypeScript 5.5, Vite 7, Tailwind CSS, Recharts |
| **Backend** | Java 21, Spring Boot 3.5, Spring Security 6, JJWT, Maven |
| **Agent** | Python 3.10, FastAPI, Uvicorn, LibTorrent, psutil |
| **Database** | PostgreSQL 16 (prod), H2 (local dev) |
| **Edge** | Cloudflare Tunnel (`cloudflared`) on a custom domain |
| **Proxy** | nginx (serves SPA + reverse-proxies `/api/v1`) |
| **DevOps** | Docker, Docker Compose, GitHub Actions (CI), JUnit 5, pytest |

---

## 🔐 Security

- JWT authentication — `POST /api/v1/auth/login` returns a Bearer token; all other endpoints require it
- Spring Security 6 filter chain (`JwtFilter`) on every request, HMAC-SHA256 signing
- CORS restricted to `https://sixeyes.alcaria.dev` + localhost dev origins
- Python agent is not internet-exposed (internal docker network only), runs as non-root
- Download path containment + torrent file-count/size caps on the agent
- No credentials in the codebase — all secrets via environment variables

---

## 🚀 Quick Start

### Prerequisites
- Docker Desktop (enable file sharing for `C:\` and `D:\`)
- A Cloudflare account with a domain (here: `sixeyes.alcaria.dev`)

### 1. Clone and configure
```bash
git clone https://github.com/EduardoAlcaria/sixeyes
cd sixeyes
cp .env.example .env
```
Fill in `.env` (DB creds, `JWT_SECRET`, admin creds, `DOWNLOAD_PATH`, and `CF_TUNNEL_TOKEN`).

### 2. Set up the Cloudflare Tunnel (one-time)
1. Cloudflare **Zero Trust → Networks → Tunnels → Create a tunnel** (named, e.g. `sixeyes`).
2. Copy the tunnel **token** into `.env` as `CF_TUNNEL_TOKEN`.
3. Add a **Public Hostname**: `sixeyes.alcaria.dev` → service `http://sixeyes-frontend:80`.
   Cloudflare auto-creates the proxied `CNAME` to `<tunnel-id>.cfargotunnel.com`.

### 3. Start everything
```bash
docker compose up -d --build
```
Brings up: PostgreSQL → Python agent → Java middleware → nginx frontend → cloudflared.

### 4. Access
| Service | URL |
|---------|-----|
| App (public, via tunnel) | https://sixeyes.alcaria.dev |
| App (local) | http://localhost:4000 |
| API health (local, through nginx) | http://localhost:4000/api/v1/torrents/test |

> The Python agent (`:9999`) and PostgreSQL (`:5432`) are internal to the docker network and intentionally not published for public access.

---

## ⚡ End-to-End Flow

1. **React** sends a request with a JWT Bearer token to `/api/v1/...` (same origin).
2. **nginx** serves the SPA and reverse-proxies `/api/v1/*` to the Java middleware.
3. **Spring** validates the token, forwards the command to the Python agent over the docker network.
4. **Python** executes the LibTorrent operation, returns raw data.
5. **Spring** formats speeds, maps status enums, persists to PostgreSQL, returns clean JSON.

**Add by `.torrent` file:** the frontend uploads the file to `POST /api/v1/torrents/addFile`; Java forwards the bytes to the agent's `/python/parseMagnet`, which returns a magnet URI, then the normal add flow runs (dedup, storage check, download).

---

## 💥 CI

GitHub Actions runs **build + tests only** (no cloud deploy — deployment is `git pull` + `docker compose up -d --build` on the host):
- **Backend:** `mvn clean test`
- **Frontend:** `npm ci` + `npm run build`

---

## 📬 Contact

**Made with 🔥 by Eduardo Alcaria**

💼 [LinkedIn](https://linkedin.com/in/eduardoalcaria)
🐙 [GitHub](https://github.com/EduardoAlcaria)

---

**License:** MIT
