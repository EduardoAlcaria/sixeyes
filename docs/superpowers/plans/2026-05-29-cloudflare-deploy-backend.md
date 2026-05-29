# Plan A — Cloudflare Deploy + Backend Migration

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Commit/push policy (SixEyes):** ONE file change per commit, push after each commit, NO `Co-Authored-By` trailer. Where a task lists multiple files, split into one commit per file in the order shown. Commit step blocks group commits per file.

**Goal:** Replace GCP Cloud Run + ngrok with a single Cloudflare Tunnel serving `sixeyes.alcaria.dev` (path-based `/api/v1`), version all backend routes under `/api/v1`, and add `.torrent` file upload support.

**Architecture:** nginx (frontend container) serves the React build at `/` and reverse-proxies `/api/v1/*` to `sixeyes-java:9090`. A `cloudflared` container exposes nginx to the internet via a named tunnel. Python agent + Postgres stay internal on the docker network. Spring `server.servlet.context-path: /api/v1` versions every route in one place.

**Tech Stack:** Spring Boot 3.5.9 / Java 21, FastAPI + libtorrent (Python), nginx, Docker Compose, Cloudflare Tunnel (`cloudflared`), GitHub Actions.

---

## File Structure

| File | Responsibility | Action |
|---|---|---|
| `java/controller/src/main/resources/application.yml` | add `server.servlet.context-path: /api/v1` | Modify |
| `java/controller/.../controller/TorrentController.java` | `@RequestMapping("/api/torrents")` → `/torrents`; add `/addFile` | Modify |
| `java/controller/.../controller/SystemInfoController.java` | `/api/system` → `/system` | Modify |
| `java/controller/.../controller/SettingsController.java` | `/api/settings` → `/settings` | Modify |
| `java/controller/.../controller/AuthController.java` | stays `/auth` (verify) | Verify |
| `java/controller/.../config/CorsConfig.java` | lock origins to domain + localhost | Modify |
| `java/controller/.../service/PythonClientService.java` | add `magnetFromFile(bytes)` call | Modify |
| `java/controller/.../service/TorrentService.java` | add `addTorrentFromFile(bytes)` | Modify |
| `python/app/routes/torrent_routes.py` | add `POST /python/parseMagnet` | Modify |
| `python/app/services/torrent_service.py` | add `magnet_from_torrent_bytes()` | Modify |
| `js/nginx.conf` | consolidated conf, port 80, `/api/v1` proxy | Modify (becomes the only conf) |
| `js/torrent-dashboard/nginx.conf` | delete (redundant) | Delete |
| `js/Dockerfile` | `EXPOSE 80`, copy `../nginx.conf` | Modify |
| `docker-compose.yml` | remove ngrok, add cloudflared, fix healthchecks | Modify |
| `.env.example` | add `CF_TUNNEL_TOKEN`, `DOMAIN`; remove ngrok/gcp | Modify |
| `.github/workflows/JavaPipeLine.yml` | test-only, no deploy | Modify |
| `.github/workflows/frontend-ci-cd-pipeline.yml` | build-only, no deploy | Modify |
| `README.md` | document tunnel + DNS setup | Modify |

---

## Task 1: Version backend routes under `/api/v1`

**Files:**
- Modify: `java/controller/src/main/resources/application.yml`
- Modify: `java/controller/src/main/java/com/sixeyes/controller/TorrentController.java:19`
- Modify: `java/controller/src/main/java/com/sixeyes/controller/SystemInfoController.java:15`
- Modify: `java/controller/src/main/java/com/sixeyes/controller/SettingsController.java:12`
- Test: `java/controller/src/test/java/com/sixeyes/controller/RoutingIT.java` (new)

- [ ] **Step 1: Add context-path to application.yml**

In `application.yml`, change the `server` block:

```yaml
server:
  port: 9090
  servlet:
    context-path: /api/v1
```

- [ ] **Step 2: Trim controller mappings**

`TorrentController.java` line 19:
```java
@RequestMapping("/torrents")
```
`SystemInfoController.java` line 15:
```java
@RequestMapping("/system")
```
`SettingsController.java` line 12:
```java
@RequestMapping("/settings")
```
`AuthController.java` — leave `@RequestMapping("/auth")` (final path `/api/v1/auth/login`).

> Note: Spring Security `requestMatchers` match the path **after** context-path is stripped, so `SecurityConfig`'s `/auth/**` permit rule still works unchanged. Verify in Step 4.

- [ ] **Step 3: Write integration test for versioned health route**

Create `java/controller/src/test/java/com/sixeyes/controller/RoutingIT.java`:

```java
package com.sixeyes.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RoutingIT {

    @Autowired MockMvc mvc;

    @Test
    void healthEndpointIsUnderApiV1() throws Exception {
        mvc.perform(get("/api/v1/torrents/test")).andExpect(status().isOk());
    }

    @Test
    void loginEndpointIsUnderApiV1AndPublic() throws Exception {
        // wrong creds -> 401 (reachable, not 403/404) proves route + permitAll
        mvc.perform(post("/api/v1/auth/login")
                .contentType("application/json")
                .content("{\"username\":\"x\",\"password\":\"y\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void torrentsListRequiresAuth() throws Exception {
        mvc.perform(get("/api/v1/torrents/get")).andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 4: Run the test**

Run: `cd java/controller && ./mvnw test -Dtest=RoutingIT`
Expected: PASS (3 tests). If `loginEndpointIsUnderApiV1AndPublic` returns 403/404, the context-path interaction with Security needs a matcher fix — adjust `SecurityConfig` `requestMatchers("/auth/**")` and re-run.

- [ ] **Step 5: Commit (one file per commit, push each)**

```bash
git add java/controller/src/main/resources/application.yml
git commit -m "feat(api): version all routes under /api/v1 via context-path"
git push
git add java/controller/src/main/java/com/sixeyes/controller/TorrentController.java
git commit -m "refactor(api): trim TorrentController mapping for /api/v1 context"
git push
git add java/controller/src/main/java/com/sixeyes/controller/SystemInfoController.java
git commit -m "refactor(api): trim SystemInfoController mapping for /api/v1 context"
git push
git add java/controller/src/main/java/com/sixeyes/controller/SettingsController.java
git commit -m "refactor(api): trim SettingsController mapping for /api/v1 context"
git push
git add java/controller/src/test/java/com/sixeyes/controller/RoutingIT.java
git commit -m "test(api): verify /api/v1 routing and auth rules"
git push
```

---

## Task 2: Lock CORS to the domain

**Files:**
- Modify: `java/controller/src/main/java/com/sixeyes/config/CorsConfig.java:17-20`

- [ ] **Step 1: Restrict allowed origins**

Replace the wildcard config in `CorsConfig.java`:

```java
config.setAllowedOriginPatterns(List.of(
        "https://sixeyes.alcaria.dev",
        "http://localhost:4000",
        "http://localhost:5173"
));
config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
config.setAllowedHeaders(List.of("*"));
config.setAllowCredentials(true);
```

- [ ] **Step 2: Run existing tests to confirm nothing breaks**

Run: `cd java/controller && ./mvnw test -Dtest=RoutingIT`
Expected: PASS (CORS config does not block same-origin MockMvc calls).

- [ ] **Step 3: Commit**

```bash
git add java/controller/src/main/java/com/sixeyes/config/CorsConfig.java
git commit -m "feat(security): restrict CORS to sixeyes.alcaria.dev and localhost"
git push
```

---

## Task 3: Python — derive magnet from a `.torrent` file

**Files:**
- Modify: `python/app/services/torrent_service.py`
- Modify: `python/app/routes/torrent_routes.py`
- Test: `python/tests/test_parse_magnet.py` (new)
- Modify: `python/requirements.txt` (add `pytest` for CI) — only if not already a dev dep

- [ ] **Step 1: Add service function**

Append to `python/app/services/torrent_service.py`:

```python
def magnet_from_torrent_bytes(data: bytes) -> str:
    """Parse raw .torrent file bytes and return a magnet URI."""
    info = lt.torrent_info(lt.bdecode(data))
    return lt.make_magnet_uri(info)
```

- [ ] **Step 2: Add route**

In `python/app/routes/torrent_routes.py`, add import and route:

```python
from fastapi import APIRouter, HTTPException, UploadFile, File
```
```python
@router.post("/parseMagnet")
async def parse_magnet(file: UploadFile = File(...)):
    raw = await file.read()
    try:
        return {"magnet": torrent_service.magnet_from_torrent_bytes(raw)}
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Invalid .torrent file: {e}")
```

- [ ] **Step 3: Write the test**

Create `python/tests/test_parse_magnet.py`:

```python
import libtorrent as lt
import pytest
from app.services import torrent_service


def _make_torrent_bytes(tmp_path) -> bytes:
    # Build a minimal single-file torrent
    f = tmp_path / "hello.txt"
    f.write_text("hello sixeyes")
    fs = lt.file_storage()
    lt.add_files(fs, str(f))
    t = lt.create_torrent(fs)
    t.add_tracker("udp://tracker.example.com:80")
    lt.set_piece_hashes(t, str(tmp_path))
    return lt.bencode(t.generate())


def test_magnet_from_torrent_bytes(tmp_path):
    data = _make_torrent_bytes(tmp_path)
    magnet = torrent_service.magnet_from_torrent_bytes(data)
    assert magnet.startswith("magnet:?xt=urn:btih:")


def test_invalid_bytes_raise(tmp_path):
    with pytest.raises(Exception):
        torrent_service.magnet_from_torrent_bytes(b"not a torrent")
```

- [ ] **Step 4: Run the test**

Run: `cd python && .venv/Scripts/python -m pytest tests/test_parse_magnet.py -v`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add python/app/services/torrent_service.py
git commit -m "feat(agent): parse .torrent bytes into a magnet URI"
git push
git add python/app/routes/torrent_routes.py
git commit -m "feat(agent): add POST /python/parseMagnet endpoint"
git push
git add python/tests/test_parse_magnet.py
git commit -m "test(agent): cover magnet parsing from .torrent bytes"
git push
```

---

## Task 4: Java — accept `.torrent` upload and reuse add flow

**Files:**
- Modify: `java/controller/src/main/java/com/sixeyes/service/PythonClientService.java`
- Modify: `java/controller/src/main/java/com/sixeyes/service/TorrentService.java`
- Modify: `java/controller/src/main/java/com/sixeyes/controller/TorrentController.java`
- Test: `java/controller/src/test/java/com/sixeyes/service/TorrentServiceFileTest.java` (new)

- [ ] **Step 1: Add Python client call**

In `PythonClientService.java`, add a method that posts the file bytes to the agent and returns the magnet. Match the existing RestTemplate/base-url pattern already used in the class (`${python.service.url}` + `/python/...`):

```java
public String magnetFromFile(byte[] torrentBytes, String filename) {
    org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
    headers.setContentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA);

    org.springframework.core.io.ByteArrayResource resource =
            new org.springframework.core.io.ByteArrayResource(torrentBytes) {
                @Override public String getFilename() { return filename; }
            };

    org.springframework.util.MultiValueMap<String, Object> body =
            new org.springframework.util.LinkedMultiValueMap<>();
    body.add("file", resource);

    var entity = new org.springframework.http.HttpEntity<>(body, headers);
    var resp = restTemplate.postForObject(pythonBaseUrl + "/python/parseMagnet", entity, java.util.Map.class);
    if (resp == null || resp.get("magnet") == null) {
        throw new InvalidMagnetException("(file) " + filename);
    }
    return resp.get("magnet").toString();
}
```
> Adapt `restTemplate` and `pythonBaseUrl` field names to whatever `PythonClientService` already declares (read the top of the file first).

- [ ] **Step 2: Add service method that reuses addTorrent**

In `TorrentService.java`, add:

```java
public TorrentResponse addTorrentFromFile(byte[] torrentBytes, String filename) {
    String magnet = pythonClient.magnetFromFile(torrentBytes, filename);
    return addTorrent(magnet);
}
```
This reuses dedup, storage validation, persistence, and download start.

- [ ] **Step 3: Add controller endpoint**

In `TorrentController.java`, add (imports `org.springframework.web.multipart.MultipartFile`):

```java
@PostMapping(value = "/addFile", consumes = "multipart/form-data")
public ResponseEntity<TorrentResponse> addFile(@RequestParam("file") MultipartFile file) throws java.io.IOException {
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(torrentService.addTorrentFromFile(file.getBytes(), file.getOriginalFilename()));
}
```

- [ ] **Step 4: Write the unit test**

Create `java/controller/src/test/java/com/sixeyes/service/TorrentServiceFileTest.java`:

```java
package com.sixeyes.service;

import com.sixeyes.dto.response.TorrentResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TorrentServiceFileTest {

    @Mock com.sixeyes.repo.TorrentRepository repo;
    @Mock PythonClientService pythonClient;
    @Mock SettingsService settingsService;
    @InjectMocks TorrentService service;

    @Test
    void addTorrentFromFileParsesThenDelegatesToAddTorrent() {
        byte[] bytes = "fake".getBytes();
        String magnet = "magnet:?xt=urn:btih:abc";
        when(pythonClient.magnetFromFile(any(), anyString())).thenReturn(magnet);
        when(repo.existsByMagnet(magnet)).thenReturn(false);
        when(settingsService.getDownloadPath()).thenReturn("/app/downloads");
        when(pythonClient.fetchDisks()).thenReturn(java.util.List.of());
        when(repo.save(any())).thenAnswer(i -> {
            var t = (com.sixeyes.model.Torrent) i.getArgument(0);
            return t;
        });

        TorrentResponse res = service.addTorrentFromFile(bytes, "x.torrent");
        assertThat(res).isNotNull();
    }
}
```
> If `assertj` is not on the classpath, use JUnit `assertNotNull`. Verify the `@InjectMocks` field set matches `TorrentService`'s constructor (repo, pythonClient, settingsService).

- [ ] **Step 5: Run the test**

Run: `cd java/controller && ./mvnw test -Dtest=TorrentServiceFileTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add java/controller/src/main/java/com/sixeyes/service/PythonClientService.java
git commit -m "feat(agent-client): post .torrent bytes to parseMagnet"
git push
git add java/controller/src/main/java/com/sixeyes/service/TorrentService.java
git commit -m "feat(torrents): add addTorrentFromFile reusing add flow"
git push
git add java/controller/src/main/java/com/sixeyes/controller/TorrentController.java
git commit -m "feat(api): add POST /api/v1/torrents/addFile multipart endpoint"
git push
git add java/controller/src/test/java/com/sixeyes/service/TorrentServiceFileTest.java
git commit -m "test(torrents): cover file upload add flow"
git push
```

---

## Task 5: Consolidate nginx to one conf serving SPA + `/api/v1` proxy

**Files:**
- Modify: `js/nginx.conf` (this becomes the single source)
- Delete: `js/torrent-dashboard/nginx.conf`
- Modify: `js/Dockerfile`

- [ ] **Step 1: Replace `js/nginx.conf`**

```nginx
server {
    listen 80;
    server_name _;
    root /usr/share/nginx/html;
    index index.html;

    gzip on;
    gzip_types text/plain text/css application/json application/javascript image/svg+xml;
    gzip_min_length 1024;

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

    # Cache hashed static assets
    location ~* \.(js|css|png|svg|ico|woff2?)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # SPA fallback
    location / {
        try_files $uri $uri/ /index.html;
        add_header Cache-Control "no-cache";
    }

    error_page 500 502 503 504 /50x.html;
    location = /50x.html { root /usr/share/nginx/html; }

    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
}
```

- [ ] **Step 2: Update `js/Dockerfile`**

Ensure it copies `js/nginx.conf` (build context is `./js`) and exposes 80:

```dockerfile
FROM node:22-alpine AS builder
WORKDIR /app
COPY torrent-dashboard/package*.json ./
RUN npm ci
COPY torrent-dashboard/ ./
RUN npm run build

FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

- [ ] **Step 3: Delete the redundant conf**

```bash
git rm js/torrent-dashboard/nginx.conf
```

- [ ] **Step 4: Verify nginx config syntax via a throwaway build**

Run: `docker build -t sixeyes-fe-test ./js && docker run --rm --entrypoint nginx sixeyes-fe-test -t`
Expected: `syntax is ok` / `test is successful`. (Frontend `dist` may be the old build — that is fine for a syntax check.)

- [ ] **Step 5: Commit**

```bash
git add js/nginx.conf
git commit -m "feat(nginx): single conf serving SPA + /api/v1 reverse proxy"
git push
git add js/Dockerfile
git commit -m "build(frontend): nginx listens on 80, copy consolidated conf"
git push
git rm js/torrent-dashboard/nginx.conf
git commit -m "chore(nginx): remove redundant dashboard nginx.conf"
git push
```

---

## Task 6: docker-compose — drop ngrok, add cloudflared, fix healthchecks

**Files:**
- Modify: `docker-compose.yml`
- Modify: `.env.example`

- [ ] **Step 1: Remove the `ngrok-db` service**

Delete the entire `ngrok-db:` block (lines ~23-36) from `docker-compose.yml`.

- [ ] **Step 2: Fix java healthcheck path**

In the `java-backend` service, update the healthcheck test URL to the versioned path:

```yaml
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9090/api/v1/torrents/test"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 30s
```

- [ ] **Step 3: Add the `cloudflared` service**

Append under `services:` (before `networks:`):

```yaml
  cloudflared:
    image: cloudflare/cloudflared:latest
    container_name: sixeyes-tunnel
    restart: unless-stopped
    command: tunnel --no-autoupdate run
    environment:
      - TUNNEL_TOKEN=${CF_TUNNEL_TOKEN}
    depends_on:
      - frontend
    networks:
      - sixeyes-net
```

> The tunnel's public hostname (`sixeyes.alcaria.dev` → `http://sixeyes-frontend:80`) is configured in the Cloudflare Zero Trust dashboard and bound to the token. No local config file needed.

- [ ] **Step 4: Update `.env.example`**

Remove `NGROK_AUTHTOKEN` and any GCP keys. Add:

```dotenv
# Cloudflare named tunnel token (Zero Trust > Networks > Tunnels)
CF_TUNNEL_TOKEN=
# Public domain served by the tunnel
DOMAIN=sixeyes.alcaria.dev
```

- [ ] **Step 5: Validate compose**

Run: `docker compose config`
Expected: prints merged config with no `ngrok-db`, includes `cloudflared`, java healthcheck shows `/api/v1/torrents/test`. No errors.

- [ ] **Step 6: Commit**

```bash
git add docker-compose.yml
git commit -m "feat(deploy): replace ngrok with cloudflared, fix api healthcheck"
git push
git add .env.example
git commit -m "chore(env): add CF_TUNNEL_TOKEN/DOMAIN, drop ngrok/gcp keys"
git push
```

---

## Task 7: Replace GCP CI/CD with build+test-only workflows

**Files:**
- Modify: `.github/workflows/JavaPipeLine.yml`
- Modify: `.github/workflows/frontend-ci-cd-pipeline.yml`

- [ ] **Step 1: Rewrite Java workflow (test only)**

Replace the whole file with:

```yaml
name: Backend Java CI

on:
  push:
    branches: [dev, master]
    paths: ['java/controller/**', '.github/workflows/JavaPipeLine.yml']
  pull_request:
    branches: [dev, master]
    paths: ['java/controller/**']

jobs:
  test:
    name: Build & Test
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: java/controller
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: 'maven'
      - name: Build & test
        run: mvn -B clean test
      - name: Publish test results
        if: github.event_name == 'pull_request'
        uses: EnricoMi/publish-unit-test-result-action@v2
        with:
          files: java/controller/target/surefire-reports/*.xml
          comment_mode: always
```

- [ ] **Step 2: Rewrite frontend workflow (build only)**

Replace the whole file with:

```yaml
name: Frontend CI

on:
  push:
    branches: [dev, master]
    paths: ['js/torrent-dashboard/**', '.github/workflows/frontend-ci-cd-pipeline.yml']
  pull_request:
    branches: [dev, master]
    paths: ['js/torrent-dashboard/**']

jobs:
  build:
    name: Lint & Build
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: js/torrent-dashboard
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '22'
          cache: 'npm'
          cache-dependency-path: js/torrent-dashboard/package-lock.json
      - run: npm ci
      - run: npm run lint || echo "lint not configured"
      - run: npm run build
      - name: Verify dist
        run: test -d dist && echo "build ok"
```

- [ ] **Step 3: Validate YAML**

Run: `python -c "import yaml,sys; [yaml.safe_load(open(f)) for f in ['.github/workflows/JavaPipeLine.yml','.github/workflows/frontend-ci-cd-pipeline.yml']]; print('yaml ok')"`
Expected: `yaml ok`.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/JavaPipeLine.yml
git commit -m "ci: java workflow build+test only, drop Cloud Run deploy"
git push
git add .github/workflows/frontend-ci-cd-pipeline.yml
git commit -m "ci: frontend workflow build only, drop Cloud Run deploy"
git push
```

---

## Task 8: Document tunnel + DNS setup, scrub GCP/ngrok mentions

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Add a deployment section to README.md**

Add (adjust to existing README structure):

```markdown
## Deployment — Cloudflare Tunnel

The whole app is served on `https://sixeyes.alcaria.dev` through one Cloudflare Tunnel.
No public ports, no GCP, no ngrok.

### One-time setup
1. Cloudflare Zero Trust → Networks → Tunnels → **Create tunnel** (named, e.g. `sixeyes`).
2. Copy the tunnel **token** → put it in `.env` as `CF_TUNNEL_TOKEN`.
3. Add a **Public Hostname**: `sixeyes.alcaria.dev` → service `http://sixeyes-frontend:80`.
   Cloudflare auto-creates the `CNAME` to `<tunnel-id>.cfargotunnel.com` (proxied).

### Run
```bash
cp .env.example .env   # fill in secrets
docker compose up -d --build
```
nginx serves the React build at `/` and proxies `/api/v1/*` to the Java middleware.
The Python agent and Postgres stay internal on the docker network.
```

- [ ] **Step 2: Scrub stale references**

Run: `grep -rin "gcloud\|cloud run\|artifact registry\|ngrok\|gcp" README.md`
Expected: no matches (remove any that remain).

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: document Cloudflare Tunnel deploy, remove GCP/ngrok"
git push
```

---

## Task 9: Full-stack verification

- [ ] **Step 1: Build and run the stack**

Run: `docker compose up -d --build`
Expected: all services healthy (`docker compose ps` shows `healthy` for db/python/java).

- [ ] **Step 2: API reachable through nginx on the versioned path**

Run: `curl -s http://localhost:4000/api/v1/torrents/test`
Expected: `{"status":"UP","service":"SixEyes"}`.

- [ ] **Step 3: SPA served at root**

Run: `curl -s -o /dev/null -w "%{http_code}" http://localhost:4000/`
Expected: `200`.

- [ ] **Step 4: Tunnel up (after dashboard config)**

Run: `curl -s https://sixeyes.alcaria.dev/api/v1/torrents/test`
Expected: `{"status":"UP",...}`.

- [ ] **Step 5: No stale cloud references in repo**

Run: `grep -rin "gcloud\|cloud run\|ngrok" --include=*.yml --include=*.yaml --include=*.md --include=*.properties . | grep -v node_modules`
Expected: no matches.

---

## Self-Review Notes

- Spec §"Remove" / "Add" / nginx / route versioning / CI / `.env` / `.torrent` upload → Tasks 1-9 cover each.
- Type consistency: `magnetFromFile(byte[], String)` (PythonClientService) ↔ `addTorrentFromFile(byte[], String)` (TorrentService) ↔ controller passes `file.getBytes()`, `file.getOriginalFilename()`. `magnet_from_torrent_bytes(bytes)` (python) ↔ route `/python/parseMagnet` returns `{"magnet": ...}` ↔ Java reads `resp.get("magnet")`.
- Adapt-on-read flags called out where field/dep names must be confirmed (`PythonClientService` internals, assertj availability).
