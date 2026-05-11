# SixEyes — AutomationHub Demo

This branch (`demonstration`) is configured for deployment via [AutomationHub](https://github.com/EduardoAlcaria/AutomationHub).

## Deploy via AutomationHub

### Option 1 — Full stack (monorepo, docker compose)

AutomationHub detects `docker-compose.yml` at the repo root and runs:
```
docker compose up --build -d
```

This starts: PostgreSQL → ngrok TCP tunnel → Python agent → Java API → React frontend

**Before deploying**, write a `.env` file on the target machine (see `.env.example`):
```bash
cp .env.example .env
# edit .env with your real values
```

Ports exposed:
| Service         | Port  |
|-----------------|-------|
| React frontend  | 4000  |
| Java API        | 9090  |
| Python agent    | 9999  |
| ngrok inspector | 4040  |

### Option 2 — Frontend only (Node.js)

To deploy just the dashboard as a Node.js app, point AutomationHub at the
`js/torrent-dashboard` subdirectory — or use a branch that has a root `package.json`
(see `frontend-only` branch if it exists).

## Two-repo pattern

You can also model this as two separate AutomationHub deployments:
- **sixeyes-frontend** — repo: this repo, branch: `demonstration`
- **sixeyes-api** — repo: this repo, branch: `demonstration`

Each clones the full repo and runs its respective Dockerfile independently.
