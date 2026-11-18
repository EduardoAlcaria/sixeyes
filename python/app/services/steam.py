"""The local Steam library, read straight from Steam's own files - no API key.

steamapps/libraryfolders.vdf lists every library, each appmanifest_<id>.acf one installed
game, and userdata/<account>/config/localconfig.vdf the minutes played per app.
"""
import glob
import os
import time

_cache: dict = {"at": 0.0, "games": []}
_TTL = 60

# Steam ships these as "apps", but nobody plays them - and an always-running one like
# Wallpaper Engine would otherwise log a "playing" session around the clock.
_NOT_GAMES = {
    "228980",   # Steamworks Common Redistributables
    "431960",   # Wallpaper Engine
    "250820",   # SteamVR
    "1070560",  # Steam Linux Runtime
    "1391110",  # Steam Linux Runtime - Soldier
}
_NOT_GAME_WORDS = ("redistributable", "proton ", "steam linux runtime", "steamvr")


def parse_vdf(text: str) -> dict:
    """Valve KeyValues text: "key" "value" pairs and "key" { ... } blocks."""
    tokens = []
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        if c == '"':
            j = i + 1
            buf = []
            while j < n and text[j] != '"':
                if text[j] == "\\" and j + 1 < n:
                    j += 1
                buf.append(text[j])
                j += 1
            tokens.append(("s", "".join(buf)))
            i = j + 1
        elif c in "{}":
            tokens.append((c, c))
            i += 1
        elif c == "/" and text.startswith("//", i):
            while i < n and text[i] != "\n":
                i += 1
        else:
            i += 1

    def block(pos: int) -> tuple[dict, int]:
        out: dict = {}
        while pos < len(tokens):
            kind, val = tokens[pos]
            if kind == "}":
                return out, pos + 1
            if kind != "s":
                pos += 1
                continue
            if pos + 1 < len(tokens) and tokens[pos + 1][0] == "{":
                child, pos = block(pos + 2)
                out[val] = child
            elif pos + 1 < len(tokens):
                out[val] = tokens[pos + 1][1]
                pos += 2
            else:
                pos += 1
        return out, pos

    return block(0)[0]


def _get(d: dict, key: str):
    """Steam is inconsistent about key case (Software/software, apps/Apps)."""
    if not isinstance(d, dict):
        return None
    lk = key.lower()
    for k, v in d.items():
        if k.lower() == lk:
            return v
    return None


def _read(path: str) -> dict:
    try:
        with open(path, encoding="utf-8", errors="replace") as f:
            return parse_vdf(f.read())
    except OSError:
        return {}


def steam_root() -> str | None:
    if os.name == "nt":
        try:
            import winreg
            with winreg.OpenKey(winreg.HKEY_CURRENT_USER, r"Software\Valve\Steam") as k:
                path = winreg.QueryValueEx(k, "SteamPath")[0]
                if os.path.isdir(path):
                    return os.path.normpath(path)
        except OSError:
            pass
    for guess in (r"C:\Program Files (x86)\Steam", r"C:\Program Files\Steam"):
        if os.path.isdir(guess):
            return guess
    return None


def library_paths(root: str | None = None) -> list[str]:
    root = root or steam_root()
    if not root:
        return []
    paths = [root]
    folders = _get(_read(os.path.join(root, "steamapps", "libraryfolders.vdf")), "libraryfolders") or {}
    for entry in folders.values():
        p = _get(entry, "path") if isinstance(entry, dict) else entry
        if isinstance(p, str) and os.path.isdir(p):
            paths.append(os.path.normpath(p))
    seen, out = set(), []
    for p in paths:
        if os.path.normcase(p) not in seen:
            seen.add(os.path.normcase(p))
            out.append(p)
    return out


_STEAMID64_BASE = 76561197960265728  # SteamID64 = base + userdata account id


def _playtime(root: str) -> dict[str, dict]:
    """appid -> {minutes, lastPlayed} for the signed-in account.

    userdata/ holds every account that ever logged in on this PC (family, friends);
    summing them mixed someone else's hours in. Falls back to all when unknown.
    """
    result: dict[str, dict] = {}
    configs = glob.glob(os.path.join(root, "userdata", "*", "config", "localconfig.vdf"))
    sid = account_id()
    if sid:
        mine = os.path.join(root, "userdata", str(int(sid) - _STEAMID64_BASE), "config", "localconfig.vdf")
        if os.path.isfile(mine):
            configs = [mine]
    for cfg in configs:
        store = _get(_read(cfg), "UserLocalConfigStore")
        apps = _get(_get(_get(_get(store, "Software"), "Valve"), "Steam"), "apps") or {}
        for appid, info in apps.items():
            if not isinstance(info, dict):
                continue
            minutes = int(_get(info, "Playtime") or 0)
            last = int(_get(info, "LastPlayed") or 0)
            r = result.setdefault(appid, {"minutes": 0, "lastPlayed": 0})
            r["minutes"] += minutes
            r["lastPlayed"] = max(r["lastPlayed"], last)
    return result


def library(force: bool = False) -> list[dict]:
    if not force and time.time() - _cache["at"] < _TTL:
        return _cache["games"]
    root = steam_root()
    if not root:
        _cache.update(at=time.time(), games=[])
        return []
    played = _playtime(root)
    games = []
    for lib in library_paths(root):
        for acf in glob.glob(os.path.join(lib, "steamapps", "appmanifest_*.acf")):
            app = _get(_read(acf), "AppState") or {}
            appid = str(_get(app, "appid") or "")
            name = _get(app, "name") or ""
            if not appid or appid in _NOT_GAMES or any(w in name.lower() for w in _NOT_GAME_WORDS):
                continue
            installdir = _get(app, "installdir") or ""
            pt = played.get(appid, {})
            games.append({
                "appId": appid,
                "name": name,
                "installDir": os.path.join(lib, "steamapps", "common", installdir),
                "library": lib,
                "sizeBytes": int(_get(app, "SizeOnDisk") or 0),
                "playtimeMinutes": pt.get("minutes", 0),
                "lastPlayed": pt.get("lastPlayed") or int(_get(app, "LastPlayed") or 0) or None,
                "coverUrl": f"https://cdn.cloudflare.steamstatic.com/steam/apps/{appid}/library_600x900.jpg",
                "headerUrl": f"https://cdn.cloudflare.steamstatic.com/steam/apps/{appid}/header.jpg",
            })
    games.sort(key=lambda g: g["name"].lower())
    _cache.update(at=time.time(), games=games)
    return games


def owned_not_installed() -> list[dict]:
    """Apps in this machine's Steam libraries that are not installed.

    Two local sources, no login needed: localconfig.vdf (every app an account has touched,
    with playtime) and the account's librarycache, where the client caches data for apps in
    the library - including games never launched. Both include DLC and tools; the controller
    keeps only real games by asking the Steam store.
    """
    root = steam_root()
    if not root:
        return []
    installed = {g["appId"] for g in library()}
    played = _playtime(root)
    ids = set(played)
    # appcache/librarycache is shared by every account on the PC; the per-account one under
    # userdata/<id>/config/librarycache only holds this account's library.
    sid = account_id()
    mine = os.path.join(root, "userdata", str(int(sid) - _STEAMID64_BASE), "config", "librarycache") if sid else ""
    if os.path.isdir(mine):
        ids |= {f[:-5] for f in os.listdir(mine) if f.endswith(".json") and f[:-5].isdigit()}
    else:
        cache = os.path.join(root, "appcache", "librarycache")
        if os.path.isdir(cache):
            ids |= {d for d in os.listdir(cache) if d.isdigit()}
    out = []
    for a in ids:
        if a in installed or a in _NOT_GAMES:
            continue
        p = played.get(a, {})
        out.append({"appId": a, "playtimeMinutes": p.get("minutes", 0), "lastPlayed": p.get("lastPlayed") or None})
    return out


def account_id() -> str | None:
    """SteamID64 of the account that last signed in here (loginusers.vdf MostRecent)."""
    root = steam_root()
    if not root:
        return None
    users = _get(_read(os.path.join(root, "config", "loginusers.vdf")), "users") or {}
    ids = [k for k, v in users.items() if k.isdigit() and isinstance(v, dict)]
    recent = [k for k in ids if str(_get(users[k], "MostRecent") or "0") == "1"]
    return (recent or ids or [None])[0]


def install(app_id: str) -> None:
    """Opens Steam's own install dialog for the app on this PC."""
    if not str(app_id).isdigit():
        raise ValueError("invalid app id")
    os.startfile(f"steam://install/{int(app_id)}")  # noqa - Windows only, like the agent


def launch(app_id: str) -> None:
    if not str(app_id).isdigit():
        raise ValueError("invalid app id")
    if not any(g["appId"] == str(app_id) for g in library()):
        raise ValueError("app is not installed")
    os.startfile(f"steam://rungameid/{int(app_id)}")  # noqa - Windows only, like the agent
