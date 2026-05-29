# SixEyes 🚀
### A hybrid torrent manager built on Cloud, Java, Python and React

Manage your torrents remotely from anywhere — no VPN, no port forwarding, no headaches. Cloud frontend and middleware talk to a local Python agent running LibTorrent on your machine, tunneled securely through ngrok.

**Simple idea. Complex engineering.**

---

## 🧱 Hybrid Architecture

SixEyes is built on three complementary pillars:

### ⚙️ **Middleware Layer**
**Spring Boot** running on **Google Cloud Run**
- JWT-authenticated RESTful API
- Orchestrates all communication with the local Python agent
- Processes and formats raw torrent data (speeds, progress, status)
- Persists torrent state to PostgreSQL (via ngrok TCP tunnel)
- Auto-scaling serverless deployment

### 🎨 **Frontend Layer**
**React + TypeScript** also on **Cloud Run**
- JWT login with session management
- Real-time torrent dashboard with 5-second polling
- Live download/upload speed charts
- Host disk monitoring (Windows drives via bind mounts)
- Completed downloads tracking
- Settings page (download path per torrent)

### 🐍 **Local Agent**
**Python + FastAPI** executing directly on user's machine
- LibTorrent protocol implementation
- Exposed to the cloud via **ngrok HTTP tunnel**
- Real-time torrent operations (add, pause, resume, remove)
- Host filesystem and disk usage reporting

### 💾 **Local Database**
**PostgreSQL 16** running in Docker on user's machine
- Exposed to Cloud Run via **ngrok TCP tunnel**
- Torrents persist across container restarts
- Auto-created schema via Hibernate on startup

**The bridge:** Everything local is accessed through ngrok tunnels. Spring middleware is the intelligent intermediary — it processes raw data from Python and serves clean, formatted responses to React. PostgreSQL lives alongside the Python agent, keeping torrent state durable.

---

## 🌐 Tech Stack

| Layer | Technology |
|-------|------------|
| **Frontend** | React 18.3, TypeScript 5.5, Vite 7.1, Tailwind CSS 3.4, Recharts 2.12 |
| **Backend** | Java 21, Spring Boot 3.4.1, Spring Security 6, JJWT 0.12.6, Maven |
| **Agent** | Python 3.9+, FastAPI, Uvicorn, LibTorrent, psutil |
| **Database** | PostgreSQL 16 (prod), H2 (local dev) |
| **Cloud** | Google Cloud Run, Artifact Registry (southamerica-east1) |
| **DevOps** | GitHub Actions, Docker, Docker Compose, JUnit 5 |
| **Tunnel** | ngrok (HTTP for Python agent, TCP for PostgreSQL) |

---

## 🔐 Security

- JWT authentication — `POST /auth/login` returns a Bearer token, all other endpoints require it
- Spring Security 6 filter chain with `JwtFilter` on every request
- HMAC-SHA256 token signing with a configurable secret
- CORS configured via `CorsConfigurationSource` bean (supports Cloud Run origins)
- No credentials in codebase — all secrets via environment variables / GitHub Secrets

---

## 💥 CI/CD Pipelines

**Push to `dev` → tests → merge to `main` → Docker build → deploy to Cloud Run**

### Backend Pipeline
```
1. JUnit tests (TorrentTest, SixEyesApplicationTests)
2. Merge dev → main
3. Maven package + Docker build
4. Push to Artifact Registry
5. Deploy to Cloud Run (0–10 instances, port 9090)
6. Health check: GET /api/torrents/test
```

### Frontend Pipeline
```
1. npm ci + TypeScript build (VITE_API_URL baked in at build time)
2. ESLint + bundle size analysis
3. Merge dev → main
4. Docker multi-stage build (Node → nginx)
5. Deploy to Cloud Run (0–5 instances, port 80)
6. Health check: GET /
```

---

## ⚡ End-to-End Flow

```
React UI → Spring Middleware → Python Agent → LibTorrent
   ↑              ↓                  ↓
   │        [PROCESSES &        [RAW DATA:
   │         TRANSFORMS]         speed, peers,
   │              ↓              progress, etc]
   │        [FORMATTED                ↓
   │         RESPONSE]               ↓
   └─────────────────────────────────┘
```

1. **React** sends request with JWT Bearer token
2. **Spring** validates token, forwards command to Python via ngrok HTTP
3. **Python** executes LibTorrent operation, returns raw data
4. **Spring** processes the response — formats speeds, maps status enums, persists to PostgreSQL
5. **React** receives clean JSON and updates UI

### Data Transformation Example

**Python Agent returns:**
```json
{ "downloadSpeed": 5.18, "progress": 75.23, "status": "downloading" }
```

**Spring Middleware returns to React:**
```json
{ "id": 1, "downloadSpeed": "5.18 MB/s", "progress": 75.23, "status": "Downloading", "updatedAt": "2025-01-15T10:30:45" }
```

---

## 🚀 Quick Start

### Prerequisites
- Docker Desktop (with file sharing enabled for C:\ and D:\)
- ngrok account — get your authtoken at [ngrok.com](https://ngrok.com)

### 1. Clone and configure
```bash
git clone https://github.com/eduardoalcaria/sixeyes
cd sixeyes
cp .env.example .env
```

Edit `.env` with your values:
```env
SPRING_PROFILES_ACTIVE=dev
DB_NAME=sixeyesdb
DB_USERNAME=sixeyes
DB_PASSWORD=yourpassword
JWT_SECRET=your_base64_secret
ADMIN_USERNAME=admin
ADMIN_PASSWORD=yourpassword
DOWNLOAD_PATH=./downloads
NGROK_AUTHTOKEN=your_ngrok_token
```

### 2. Start everything
```bash
docker compose up
```

This starts: PostgreSQL → ngrok TCP tunnel (port 5432) → Python agent → Java middleware → React frontend

### 3. Access
| Service | URL |
|---------|-----|
| Frontend | http://localhost:4000 |
| Java API | http://localhost:9090 |
| Python Agent | http://localhost:9999 |
| ngrok DB tunnel inspector | http://localhost:4040 |

### 4. Configure Cloud Run (production)
After `docker compose up`, open http://localhost:4040 to get the ngrok TCP URL for your database, then set these GitHub Secrets:

| Secret | Description |
|--------|-------------|
| `GCP_PROJECT_ID` | Your GCP project ID |
| `GCP_SA_KEY` | GCP service account JSON |
| `PYTHON_SERVICE_URL` | Your ngrok HTTP URL for Python agent |
| `JWT_SECRET` | HMAC-SHA256 secret (base64) |
| `ADMIN_USERNAME` | Admin login username |
| `ADMIN_PASSWORD` | Admin login password |
| `DB_URL` | `jdbc:postgresql://<ngrok-host>:<ngrok-port>/sixeyesdb` |
| `DB_USERNAME` | PostgreSQL username |
| `DB_PASSWORD` | PostgreSQL password |
| `BACKEND_API_URL` | Java Cloud Run URL (for frontend build) |

> **Note:** ngrok free tier assigns a new TCP address on every restart. Update `DB_URL` in GitHub Secrets after each `docker compose up`. A paid ngrok plan lets you reserve a static TCP address.

---

## 📊 System Architecture

```
┌─────────────────────────────────────┐
│         React Dashboard             │
│    (Cloud Run — 0-5 instances)      │
│   TypeScript + Vite + Tailwind      │
└─────────────┬───────────────────────┘
              │ REST API + JWT
              ▼
┌─────────────────────────────────────┐
│      Spring Boot Middleware         │
│    (Cloud Run — 0-10 instances)     │
│                                     │
│  ✓ JWT authentication               │
│  ✓ Validates & forwards requests    │
│  ✓ Processes raw torrent data       │
│  ✓ Formats speeds and status        │
│  ✓ Persists state to PostgreSQL     │
└──────┬──────────────────┬───────────┘
       │ HTTP (ngrok)     │ TCP (ngrok)
       ▼                  ▼
┌──────────────┐   ┌──────────────────┐
│ Python Agent │   │  PostgreSQL 16   │
│  (local)     │   │  (local Docker)  │
│  LibTorrent  │   │  Torrent state   │
│  FastAPI     │   │  persistence     │
└──────────────┘   └──────────────────┘

Region: southamerica-east1 (São Paulo)
```

---

## 🎯 What SixEyes Proves

🔹 **Hybrid architectures work** — Cloud + Local, best of both worlds
🔹 **ngrok as infrastructure** — Secure tunnels replace static IPs entirely
🔹 **Middleware processing matters** — Clean separation keeps React dumb and Python raw
🔹 **Persistence without cloud cost** — Local PostgreSQL tunneled to Cloud Run
🔹 **CI/CD from day one** — Push to production in minutes

---

## 📬 Contact

**Made with 🔥 by Eduardo Alcaria**

💼 [LinkedIn](https://linkedin.com/in/eduardoalcaria)
📧 eduardoalcarialopes@gmail.com
🐙 [GitHub](https://github.com/eduardoalcaria)

---

**License:** MIT
**Status:** ![Stable Prototype](https://img.shields.io/badge/status-stable%20prototype-green) ![CI/CD](https://img.shields.io/badge/CI%2FCD-automated-blue) ![Cloud](https://img.shields.io/badge/cloud-GCP-blue)
