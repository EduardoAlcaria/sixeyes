"""Installed games on disk: folder sizes, the executable to launch, and which are running.

Launching is a code-execution path, so it only ever starts an .exe that sits inside one of
the game roots the controller passes (the install folder and Steam libraries) - never a
download folder, never an arbitrary path.
"""
import os
import re
import subprocess
import threading
import time

import psutil

from app.services.torrent_service import _within, _prefix

_size_cache: dict[str, dict] = {}
_size_lock = threading.Lock()
_SIZE_TTL = 600

# Executables a game folder ships that are not the game.
_NOT_GAME = re.compile(
    r"(?i)^(unins|setup|install|vc_?redist|vcredist|dxsetup|dxwebsetup|directx|dotnet|ue4prereq|"
    r"ueprereq|crash|report|easyanticheat|eac|battleye|be_|quicksfv|redist|helper|update|"
    r"launcherhelper|cefprocess|quicksfv|unitycrashhandler|notification_helper|python|7z)")


def _tree_size(path: str) -> int:
    total = 0
    stack = [path]
    while stack:
        current = stack.pop()
        try:
            with os.scandir(current) as it:
                for entry in it:
                    try:
                        if entry.is_symlink():
                            continue
                        if entry.is_dir(follow_symlinks=False):
                            stack.append(entry.path)
                        else:
                            total += entry.stat(follow_symlinks=False).st_size
                    except OSError:
                        pass
        except OSError:
            pass
    return total


def _compute_sizes(root: str) -> None:
    entries = []
    try:
        with os.scandir(root) as it:
            dirs = [e for e in it if e.is_dir(follow_symlinks=False)]
    except OSError:
        dirs = []
    for d in dirs:
        entries.append({"name": d.name, "path": d.path, "bytes": _tree_size(d.path)})
    entries.sort(key=lambda e: e["bytes"], reverse=True)
    with _size_lock:
        _size_cache[_prefix(root)] = {"at": time.time(), "entries": entries, "computing": False}


def sizes(root: str) -> dict:
    """Sizes of each game folder under root. Walking hundreds of GB takes a while, so the
    first call starts it in the background and returns computing=True."""
    if not root or not os.path.isdir(root):
        raise ValueError("not a directory")
    key = _prefix(root)
    with _size_lock:
        cached = _size_cache.get(key)
        stale = cached is None or time.time() - cached["at"] > _SIZE_TTL
        if stale and not (cached and cached.get("computing")):
            _size_cache[key] = {**(cached or {"entries": []}), "at": (cached or {}).get("at", 0), "computing": True}
            threading.Thread(target=_compute_sizes, args=(root,), daemon=True).start()
        cached = _size_cache[key]
    usage = psutil.disk_usage(root)
    return {
        "root": root,
        "entries": cached.get("entries", []),
        "computing": cached.get("computing", False),
        "computedAt": int(cached.get("at") or 0) or None,
        "free": usage.free,
        "total": usage.total,
    }


def _norm_name(s: str) -> str:
    return re.sub(r"[^a-z0-9]", "", s.lower())


def find_exe(directory: str, roots: list[str]) -> str | None:
    """Best guess at the game executable: one named like its folder, else the largest."""
    prefixes = [_prefix(r) for r in roots if r]
    if not os.path.isdir(directory) or not _within(directory, prefixes):
        raise ValueError("folder is not inside a game root")
    folder = _norm_name(os.path.basename(directory.rstrip("\\/")).split("[")[0])
    candidates = []
    base_depth = directory.rstrip("\\/").count(os.sep)
    for current, dirs, files in os.walk(directory):
        if current.count(os.sep) - base_depth >= 3:
            dirs[:] = []
        dirs[:] = [d for d in dirs if d.lower() not in ("_redist", "redist", "_commonredist", "directx", "support")]
        for f in files:
            if not f.lower().endswith(".exe") or _NOT_GAME.match(f):
                continue
            full = os.path.join(current, f)
            try:
                size = os.path.getsize(full)
            except OSError:
                continue
            stem = _norm_name(os.path.splitext(f)[0])
            named = bool(folder) and (stem in folder or folder in stem)
            depth = current.count(os.sep) - base_depth
            candidates.append((named, size, -depth, full))
    if not candidates:
        return None
    candidates.sort(reverse=True)
    return candidates[0][3]


def launch(exe: str, roots: list[str]) -> int:
    prefixes = [_prefix(r) for r in roots if r and os.path.isdir(r)]
    real = os.path.realpath(exe)
    if not real.lower().endswith(".exe") or not os.path.isfile(real) or not _within(real, prefixes):
        raise ValueError("executable is not inside a game root")
    flags = getattr(subprocess, "DETACHED_PROCESS", 0) | getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0)
    proc = subprocess.Popen([real], cwd=os.path.dirname(real), creationflags=flags,
                            stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    return proc.pid


def running(roots: list[str]) -> list[dict]:
    """Processes whose executable lives under a game root - however they were started, so
    a game opened from Steam or a desktop shortcut still counts towards playtime."""
    prefixes = [_prefix(r) for r in roots if r and os.path.isdir(r)]
    if not prefixes:
        return []
    out = []
    for p in psutil.process_iter(["pid", "exe", "create_time", "name"]):
        exe = p.info.get("exe")
        if not exe or _NOT_GAME.match(os.path.basename(exe)):
            continue
        if _within(exe, prefixes):
            out.append({"pid": p.info["pid"], "exe": exe, "name": p.info.get("name"),
                        "startedAt": int(p.info.get("create_time") or 0)})
    return out
