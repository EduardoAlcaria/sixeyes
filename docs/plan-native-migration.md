# SixEyes - Native Migration Plan (drop Docker, keep tunnel)

## Goal

Run SixEyes as native Windows processes instead of 5 Docker containers. Keep remote
access via Cloudflare Tunnel. Keep full-drive browsing. Keep auto-install. Replace the
security boundary the container was providing.

Idle RAM ~2.5 GB → ~400 MB. Cold start ~40 s → ~5 s.

## Decisions

| Question | Decision |
|---|---|
| Remote access | Cloudflare Tunnel + **Cloudflare Access** (not Tailscale - only the dashboard should be exposed, and clients must work from any browser) |
| Auth transport | **Cookie-only** - httpOnly, Secure, SameSite=Strict. No token in localStorage |
| Session model | **Opaque session token + `session` table**, replacing JWT. JWT cannot be revoked without bolting server state on anyway, and there is one backend, so statelessness buys nothing |
| Database | H2 file mode (both existing `@Query` are JPQL, portable; H2 already wired in dev profile) |
| Static hosting | Spring Boot serves `dist/` - nginx container deleted, frontend and API become same-origin |
| Device trust | Device-pairing table (phase 2), not MAC whitelist - MACs do not survive routing |

## Verified facts

- `libtorrent-2.0.13-cp310/311/312/313-win_amd64.whl` exist on PyPI (downloaded and
  confirmed). Host Python is 3.14 - no wheel yet, so use a **3.12 or 3.13 venv**.
- `CatalogGameRepository.java:21,24` - only custom queries, both JPQL. No Postgres-specific SQL.
- `application-dev.yml` already runs H2 with `ddl-auto: update`. File mode is a URL change.
- `JwtFilter.java:26` reads `Authorization: Bearer` only.
- `SecurityConfig.java:24` disables CSRF - revisit under cookie auth.
- `scripts/auto-install.ps1` runs on the host already and sends a Bearer token from `.env`.

---

# Threat model

Three things can realistically cause harm. Everything else is noise, and the security
work is ordered against these, not against a generic checklist.

1. **A bot finds the tunnel hostname** and brute-forces or exploits the login endpoint.
2. **Something obtains a valid session** and uses the install queue to point execution at
   a binary it controls. Note the threat is a hijacked session redirecting the installer
   path - not the repacks themselves, which are a trusted source by operator policy.
3. **A hostile peer feeds malformed data to libtorrent** - C++ parsing untrusted network
   input continuously, by design.

The token/crypto design is a third-order concern. Boundaries and blast radius are what
matter. Two facts drive the ordering:

- Docker is currently the only reason a compromised Python agent cannot read the rest of
  the disk. Removing it without a replacement makes the app **less** safe than today.
- Auto-install is an API-triggered code-execution path. Source trust is handled by the
  operator (known repacks only); the control below constrains *which path* can be
  executed, so a hijacked session cannot repoint it.

---

# Phase 0 - De-Docker

1. **Python agent native**
   - `py -3.12 -m venv python/.venv`; `pip install -r python/requirements.txt`
   - Pin `libtorrent==2.0.13` in `requirements.txt` (see S7 for why it is tracked)
   - Bind `127.0.0.1:9999` only (was `0.0.0.0` for Docker networking)
   - Delete `HOST_DRIVES` / `/host/C` path translation - `psutil.disk_partitions()`
     now returns real `C:\`, `D:\`, every drive
   - `STATE_DIR` → a real path e.g. `./state`
2. **Database** - new `application-local.yml`, copy of dev profile with
   `jdbc:h2:file:./data/sixeyes;AUTO_SERVER=TRUE`. Drop the `db` service.
3. **Static SPA** - `npm run build`, output to
   `java/controller/src/main/resources/static/`. Add an SPA-fallback controller
   forwarding unknown non-`/api` paths to `index.html`. Delete the nginx container
   and `js/nginx.conf`. Delete `CorsConfig.java` (same origin now).
4. **Java** - `PYTHON_SERVICE_URL=http://localhost:9999` (already the default at
   `application.yml:19`). Bind `127.0.0.1:9090` so only cloudflared reaches it.
   *Knob:* set `0.0.0.0` if direct LAN access is wanted instead of tunnel-only.
5. **`start.ps1`** - two `Start-Process`, one uvicorn, one `java -jar`.
6. **cloudflared** as a Windows service (`cloudflared service install <TOKEN>`),
   tunnel target `localhost:9090`.
7. Keep `docker-compose.yml` in-repo for any real-server deploy. It stops being the
   local path.

**Exposed after phase 0:** nothing but the tunnel. Python on loopback, Java on
loopback, no Postgres, no nginx. libtorrent still needs 6881-6891 inbound for peer
connectivity - that is the BT protocol, unrelated to the dashboard.

---

# Phase 1 - Security, in shipping order

Ordered by risk reduced per hour spent. **S1-S4 ship with phase 0, not after it** -
S2 and S4 close boundaries that removing Docker actively opens.

### S1 - Cloudflare Access · ~20 min · highest ratio on the list

Identity gate in front of the tunnel. Email/Google login before any request reaches the
origin. The login page stops existing as far as the internet is concerned, and every bug
in the auth code - present and future - becomes unreachable. Kills threat 1 outright.

### S2 - Dedicated low-privilege Windows user · ~30 min · the container replacement

Both processes run as a separate standard account. Write access to the download dirs and
the app dir, nothing else - no admin rights, no access to the primary user's profile.

This is the single most important item in the migration. It is what stands between
"attacker has a session" and "attacker has the machine" - it contains a compromised
Python agent, a compromised Java process, and threat 2 alike.

### S3 - Path permissions split by operation · ~20 lines

Replaces the single flat whitelist:

- **browse / list** → all drives, read-only. Full-drive UX preserved; listing names is low harm
- **save path / mkdir** → configured roots only, via the existing `_safe_save_path()`
- **install execute** → only an `.exe` resolving inside a directory this app downloaded

The third is the only deliberate code-execution path in the app. Its job is to stop a
hijacked session from repointing the installer at an attacker-chosen file - repack trust
itself is the operator's call and is out of scope.

### S4 - Authenticate Java → Python · ~15 lines · gap created by this migration

The agent trusts anything reaching port 9999; Docker network isolation was its only
protection. On loopback, every local process and every user account on the machine can
call `/systemInfo/browse` and `/systemInfo/mkdir` with no login at all.

Shared secret generated at startup, passed to both processes, sent as a header by Java,
required by Python. Must close in the same PR that moves off Docker.

### S5 - Cookie-only auth with revocable sessions · replaces JWT

```
session
  id                  uuid pk
  token_hash          char(64) unique   -- SHA-256 of cookie value; DB leak != session theft
  user_id
  device_id           fk -> trusted_device (phase 2)
  created_at
  last_seen_at
  expires_at                            -- sliding, bumped on activity
  absolute_expires_at                   -- hard cap ~30d, cannot slide forever
  revoked_at          null
```

- Login issues a 256-bit random value, stores only its SHA-256, sets it as
  `HttpOnly; Secure; SameSite=Strict; Path=/`
- `JwtFilter` becomes `SessionFilter`: hash the cookie, look up the row, reject if
  `revoked_at` is set or either expiry has passed. Bump `last_seen_at` / `expires_at`,
  throttled to once a minute so polling does not write per request
- `POST /auth/logout` sets `revoked_at` - real invalidation, effective immediately
- `POST /auth/logout-all` revokes every row for the user
- A token presented after `revoked_at` is theft evidence → revoke the whole user's rows
- Refresh is the sliding `expires_at`. No refresh token, no rotation protocol, no clock skew
- `api.ts` drops the `Authorization` header; same-origin fetch sends the cookie
- CSRF: SameSite=Strict + same-origin means cross-site requests never carry the cookie,
  which covers it. Enable `CookieCsrfTokenRepository` only if SameSite is relaxed to Lax
- **Delete** `JwtService.java`, the JJWT dependency, `jwt.expiration-ms`
- *Interim:* keep a Bearer fallback accepting one service token so `auto-install.ps1`
  keeps working until P3-1 removes it

*Why not JWT + refresh rotation + denylist:* the access token stays cryptographically
valid after logout unless the denylist is checked on every request - at which point the
per-request DB lookup is identical to this design, with two token types, a rotation
protocol, ~230 more lines, and a signing secret whose compromise is undetectable and
forges any user indefinitely.

### S6 - Validate `Cf-Access-Jwt-Assertion` · ~30 lines

Checked in the session filter so a request reaching the origin directly cannot bypass S1.

### S7 - Track libtorrent

Threat 3. The largest attack surface in the app, larger than the entire web layer, and
the one nobody thinks about. Pinned at 2.0.13 (current) in phase 0 - review on a
schedule and update deliberately.

### Deliberately skipped

App-level 2FA (Access is already the second factor - doubling it is theater), mTLS client
certs, WAF/fail2ban rules, H2 file encryption (if they can read the file you have already
lost), a secrets manager, password complexity policy. One long random admin password in a
password manager plus an ACL on `.env` covers the same ground.

---

# Phase 2 - Device pairing

Your "approve this device" idea, implemented so it works across the internet. MAC
addresses are stripped at the first router and never reach the origin; a client-generated
device ID is the working equivalent (this is what AnyDesk actually does).

`trusted_device` table. Client generates a random 256-bit device ID on first visit and
sends it as a header. Unknown device + correct password → `403 PENDING_APPROVAL`, no
cookie issued. Approve from an already-trusted device or from the host machine. Approval
page in Settings. `session.device_id` points here, so revoking a device kills its sessions
in the same query.

# Phase 3 - Polish

**P3-1.** Collapse `auto-install.ps1` into Java `ProcessBuilder` on the completion
handler. The polling daemon only exists because containerized Java could not spawn a host
process. Removes the service token from `.env` entirely.

**P3-2.** Login rate limit. Scheduled purge of expired/revoked `session` rows.

# What gets deleted

`js/nginx.conf`, `CorsConfig.java`, `JwtService.java` + JJWT, `/host/*` mounts and path
translation, `HOST_DRIVES`, the compose stack as the local path, and eventually
`scripts/auto-install.ps1`.
