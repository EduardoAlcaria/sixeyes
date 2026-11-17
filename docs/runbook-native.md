# SixEyes - Native Runbook

Docker is no longer the local path. Two processes, one jar, one venv.

```powershell
./scripts/build.ps1     # venv + dashboard + jar
./scripts/start.ps1     # agent on 127.0.0.1:9999, controller on 127.0.0.1:9123
```

Dashboard at <http://127.0.0.1:9123/>. The SPA and the API are the same origin, so there
is no nginx and no CORS.

`docker-compose.yml` stays in the repo for deploying to a real server. It is not used here.

---

## What is exposed

| Port | Bound to | Reachable by |
|---|---|---|
| 9123 controller | `127.0.0.1` | cloudflared (same machine) |
| 9999 python agent | `127.0.0.1` | the controller |
| 6881-6891 | all | BitTorrent peers - the protocol, unrelated to the dashboard |

Nothing else. No Postgres, no nginx, no container network.

`./scripts/start.ps1 -Lan` binds the controller to `0.0.0.0` for direct LAN access.
`CF_ACCESS_REQUIRE_ASSERTION=true` only checks that the `Cf-Access-Jwt-Assertion` header is
present - it does not verify the JWT, so a LAN client can forge it. Treat it as a tripwire,
not a gate; keep the loopback default when Access is your perimeter.

---

## Manual setup - do these once, before the first remote test

### 1. Cloudflare Access (highest value item here)

The tunnel alone publishes your dashboard to the internet. Access puts an identity gate in
front of it, so unauthenticated requests never reach this machine at all.

1. Zero Trust dashboard → **Access → Applications → Add an application → Self-hosted**
2. Domain: the hostname the tunnel serves
3. Policy: **Allow**, include → **Emails** → your address (or a Google login rule)
4. Save, then load the hostname in a private window - you should be asked to authenticate
   before SixEyes renders

### 2. cloudflared as a Windows service

```powershell
cloudflared service install <TUNNEL_TOKEN>
```

Point the tunnel's public hostname at `http://localhost:9123`. It used to target the nginx
container on port 80; Spring serves the SPA now, so the origin is the controller itself.

### 3. Run as a dedicated low-privilege Windows account

This is the container replacement, and the most important item in the migration. Docker was
the only reason a compromised agent could not read the rest of the disk.

```powershell
# As administrator
net user sixeyes /add
net localgroup Administrators sixeyes /delete   # ensure it is NOT an admin

icacls "D:\Games"     /grant "sixeyes:(OI)(CI)M"
icacls "<repo path>"  /grant "sixeyes:(OI)(CI)M"
```

Then run `start.ps1` as that account - a Scheduled Task with "Run whether user is logged on
or not" is the simplest way to have it survive reboots.

Grant that account write access to the download and install directories and nothing else.
It should not be able to read your user profile.

### 4. Confine writes

```
ALLOWED_WRITE_ROOTS=D:\Games,D:\Downloads
```

Browsing stays read-only across every drive, so the folder picker still shows everything.
Only save paths and `mkdir` are restricted. Leaving this unset confines writes to the
downloads directory alone.

---

## Auto-install

`scripts/auto-install.ps1` is gone. The controller does it directly now - the polling
daemon only existed because containerised Java could not spawn a Windows `.exe`, and it
needed a long-lived API token in `.env` to talk to itself.

Set `INSTALL_ROOT=D:\Games` to enable it. Blank means install requests stay queued and
nothing is executed.

The installer is any shallowest `setup*.exe` - Monkey Repacks ship
`setup_monkey_repack.exe`, not `setup.exe`. It is discovered inside the torrent's own
download directory, resolved through symlinks, and re-checked to still be inside that
directory before it runs. That stops a
hijacked session from repointing execution at a file of its choosing. It does not vouch for
the installer itself - step 3 is what contains that.

---

## Selective download

The download dialog reads the magnet's file list before any bytes transfer. The agent adds
the torrent in libtorrent's upload mode, so the info dictionary arrives from peers and no
payload is ever written; the result is cached per magnet.

FitGirl splits optional content into `fg-optional-*` files. Those appear as checkboxes,
**unchecked by default** - the point is to skip them. Deselected files get priority 0, so
their pieces are never requested at all. That saves bandwidth and disk, not just install
time, and the repack installer omits any optional part whose file is not present, which is
FitGirl's own selective-download mechanism.

Exclusions are recorded in the resume sidecar, so a restart re-applies them rather than
quietly downloading the parts you skipped.

Cover art now comes from the repack page itself, so it matches the release being installed.
IGDB is used for metadata only, and as a cover fallback when a page has no `og:image`.

### Integrity check (mandatory)

Every install verifies the payload first, and there is no switch to turn it off - the
repack's own installer offers the same check as a step the user cannot deselect.

Repacks ship `MD5/fitgirl-bins.md5` plus a copy of QuickSFV, wired in as a `postinstall`
entry. That entry opens a GUI window and `/VERYSILENT` skips it, so neither is any use
headless. The file is an ordinary md5sum list, so `RepackVerifier` reads it directly:

```
; If you miss selective download files - that's OK
b93637acfc9d4ea796e51ab6c1c19c7f *..\fg-01.bin
```

That header is the rule that makes selective download work. Files deselected at download
time are absent on purpose and are skipped; a missing **required** file fails the install.
Checksum paths are treated as untrusted input - `..\` is normal, but an entry that climbs
out of the download directory is a failure, not a file to hash.

Torrents with no `.md5` at all are not repacks and install unverified.

Status goes `REQUESTED -> VERIFYING -> INSTALLING -> INSTALLED`, or `FAILED` with the
offending files named.

### Redists and drivers are not installed

They are declared as InnoSetup components - `directx`, `vc_2022_x86`, `vc_2022_x64`,
`dotnet471` - and the runner passes `/COMPONENTS="text"` (main game files only), which
leaves them out.

`/VERYSILENT` does **not** skip `[Run]` entries flagged `postinstall`; only `skipifsilent`
ones are skipped. Read from a real repack script: QuickSFV, the `bit.ly` site link and
`cmd /c host.cmd` (edits the Windows hosts file) run unattended and have no component to
turn off. So `install-runner.ps1` watches the setup process and kills QuickSFV, the redist
installers and `host.cmd`, and compares the hosts file hash before/after, reporting a
change in the result message. Untested against a full install.
Whether the machine has the right runtimes is the operator's problem for now.

### RAM limit: not present in any repack

None of the repacks examined declares a `[Tasks]` section or any memory setting, so there
is nothing to drive. Deliberately not implemented rather than shipped as a control that
does nothing. If a future repack declares one, it will show up in the parsed script.

## Auth

- Session is an opaque random value in an `HttpOnly; Secure; SameSite=Strict` cookie.
  JavaScript cannot read it, so an XSS in the dashboard cannot steal it.
- The server stores only `SHA-256(token)`, so a database dump yields no usable sessions.
- Logout writes `revoked_at` and takes effect on the next request. There is no window where
  a logged-out credential still works - which is exactly what JWT could not offer.
- `expires_at` slides on activity up to a hard 30-day ceiling.
- A token replayed after revocation is treated as theft: every session for that user is
  revoked.

### Devices

A random 256-bit identifier generated by the browser and stored locally. The correct
password on an unrecognised browser returns `PENDING_APPROVAL` and issues no cookie.

- The **first** device is trusted on sight - otherwise there would be no approved device
  from which to approve anything.
- Approve or remove later devices in **Settings → Devices**. Removing one revokes its
  sessions in the same query.
- MAC addresses cannot do this job: they are stripped at the first router and never reach
  the server.

Lost every trusted device? Stop the app, delete the `trusted_device` rows from
`java/controller/data/sixeyes.mv.db`, restart - the next device becomes the first again.

---

## Deliberately not done

App-level 2FA (Access already is the second factor), mTLS client certificates, WAF or
fail2ban rules, H2 file encryption, a secrets manager, password complexity policy.

---

## Notes

- `libtorrent` is pinned in `python/requirements.txt`. It is C++ parsing hostile input from
  arbitrary peers - the largest attack surface in the app, larger than the web layer. Bump
  it deliberately and on a schedule.
- No `cp314` wheel exists, so the venv must be Python 3.13 or older. `build.ps1` uses uv to
  fetch 3.13 without touching the system Python.
- Data lives in `java/controller/data/sixeyes.mv.db` (H2) and `python/state/` (libtorrent
  fast-resume blobs). Back up both.
