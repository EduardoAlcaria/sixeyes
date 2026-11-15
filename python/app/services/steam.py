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


def _playtime(root: str) -> dict[str, dict]:
    """appid -> {minutes, lastPlayed}, summed across every account that logged in here."""
    result: dict[str, dict] = {}
    for cfg in glob.glob(os.path.join(root, "userdata", "*", "config", "localconfig.vdf")):
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


def launch(app_id: str) -> None:
    if not str(app_id).isdigit():
        raise ValueError("invalid app id")
    if not any(g["appId"] == str(app_id) for g in library()):
        raise ValueError("app is not installed")
    os.startfile(f"steam://rungameid/{int(app_id)}")  # noqa - Windows only, like the agent
