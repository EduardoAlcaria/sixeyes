import json
import os
import shutil
import threading
import time

import libtorrent as lt
import psutil

from app.domain.status import TorrentStatus

DEFAULT_SAVE_PATH = os.getenv("DOWNLOAD_PATH", "downloads")
os.makedirs(DEFAULT_SAVE_PATH, exist_ok=True)

_SAVE_ROOT = os.path.realpath(DEFAULT_SAVE_PATH)

MAX_FILES = int(os.getenv("MAX_TORRENT_FILES", "10000"))
MAX_TOTAL_BYTES = int(os.getenv("MAX_TORRENT_BYTES", str(2 * 1024 ** 4)))  # 2 TiB


# --- Path permissions, split by operation -----------------------------------
# Reading a directory listing is low harm, so browsing spans every mounted drive.
# Writing is what an attacker actually wants, so save paths and mkdir are confined
# to explicitly configured roots. Execution (auto-install) is narrower still and is
# enforced on the Java side, against the directories this app actually downloaded.


def _drive_roots() -> list[tuple[str, str]]:
    """[(realpath, label)] for every mounted drive, e.g. ('C:\\', 'C:')."""
    roots: list[tuple[str, str]] = []
    for part in psutil.disk_partitions(all=False):
        mount = part.mountpoint
        if not os.path.isdir(mount):
            continue  # empty optical drive, unreadable mount
        roots.append((os.path.realpath(mount), mount.rstrip(os.sep) or mount))
    return roots


def _norm(path: str) -> str:
    """Case-folded on Windows, so D:\\Games and d:\\games compare equal."""
    return os.path.normcase(os.path.realpath(path))


def _prefix(path: str) -> str:
    """Comparison form: case-folded, no trailing separator. A drive root arrives as
    'C:\\' and must become 'c:' or every child comparison gains a doubled slash."""
    return _norm(path).rstrip(os.sep) or os.sep


def _within(path: str, prefixes: list[str]) -> bool:
    """True if path is the prefix itself or sits underneath it. Compares whole path
    segments, so 'D:\\Games' never authorises 'D:\\GamesEvil'."""
    resolved = _prefix(path)
    return any(
        resolved == p or resolved.startswith(p.rstrip(os.sep) + os.sep)
        for p in prefixes
    )


_READ_ROOTS = _drive_roots()
_READ_PREFIXES = [_prefix(r) for r, _ in _READ_ROOTS]

# Write access defaults to the downloads dir alone. Widen it deliberately via
# ALLOWED_WRITE_ROOTS (comma-separated), e.g. "D:\\Games,E:\\Downloads".
_WRITE_PREFIXES = sorted({
    _prefix(p) for p in
    [x.strip() for x in os.getenv("ALLOWED_WRITE_ROOTS", "").split(",") if x.strip()]
    + [_SAVE_ROOT]
})


def _safe_save_path(candidate: str | None) -> str:
    """Resolve candidate under a writable root; reject anything that escapes."""
    if not candidate:
        return _SAVE_ROOT
    resolved = os.path.realpath(candidate)
    if not _within(resolved, _WRITE_PREFIXES):
        raise ValueError(f"save_path not under an allowed write root: {candidate}")
    return resolved


def _device_label(path: str) -> str:
    """Which drive (C:/D:) a path lives on."""
    return os.path.splitdrive(os.path.realpath(path))[0] or "downloads"


def list_dir(path: str | None) -> dict:
    """List sub-directories. No path -> the drive roots. Read-only, spans all drives."""
    if not path:
        entries = [{"name": label, "path": root} for root, label in _READ_ROOTS]
        entries.append({"name": "downloads", "path": _SAVE_ROOT})
        return {"path": "", "parent": None, "entries": entries}

    resolved = os.path.realpath(path)
    if not _within(resolved, _READ_PREFIXES):
        raise ValueError("path not allowed")
    if not os.path.isdir(resolved):
        raise ValueError("not a directory")

    entries: list[dict] = []
    try:
        for name in sorted(os.listdir(resolved), key=str.lower):
            full = os.path.join(resolved, name)
            if os.path.isdir(full):
                entries.append({"name": name, "path": full})
    except PermissionError:
        pass

    parent = os.path.dirname(resolved)
    parent = parent if (parent != resolved and _within(parent, _READ_PREFIXES)) else None
    return {"path": resolved, "parent": parent, "entries": entries}


def make_dir(parent: str, name: str) -> dict:
    """Create a sub-directory. Write op - confined to the configured write roots."""
    if not name or "/" in name or "\\" in name or name in (".", ".."):
        raise ValueError("invalid folder name")
    base = os.path.realpath(parent)
    if not _within(base, _WRITE_PREFIXES):
        raise ValueError("parent not under an allowed write root")
    target = os.path.realpath(os.path.join(base, name))
    if not _within(target, _WRITE_PREFIXES):
        raise ValueError("target not under an allowed write root")
    os.makedirs(target, exist_ok=True)
    return {"path": target}


def magnet_from_torrent_bytes(data: bytes) -> str:
    """Parse raw .torrent file bytes and return a magnet URI."""
    info = lt.torrent_info(lt.bdecode(data))
    return lt.make_magnet_uri(info)


# --- Selective download ------------------------------------------------------
# FitGirl repacks split optional content (language packs, bonus soundtracks, hi-res
# textures) into files named fg-optional-*. Skipped parts get priority 0, so their bytes
# are never downloaded, and the repack installer omits any part whose file is absent.

# Appended to every magnet. Bare-hash magnets otherwise depend on DHT alone, which can
# take minutes to find peers (or never does on a quiet swarm).
_PUBLIC_TRACKERS = [
    "udp://tracker.opentrackr.org:1337/announce",
    "udp://open.demonii.com:1337/announce",
    "udp://open.stealth.si:80/announce",
    "udp://tracker.torrent.eu.org:451/announce",
    "udp://exodus.desync.com:6969/announce",
    "udp://tracker.dler.org:6969/announce",
]


def _magnet_params(magnet: str):
    params = lt.parse_magnet_uri(magnet)
    params.trackers = list(params.trackers) + [t for t in _PUBLIC_TRACKERS if t not in params.trackers]
    return params


_session = lt.session()
_session.listen_on(6881, 6891)

_torrents: dict[int, dict] = {}
# Set as soon as the torrent is in the session, before metadata - so pause/remove work
# while a magnet is still resolving. _handles/_hash_by_id only exist after metadata.
_handle_by_id: dict[int, lt.torrent_handle] = {}
_handles: dict[object, lt.torrent_handle] = {}
_hash_by_id: dict[int, object] = {}
_threads: dict[int, threading.Thread] = {}
_id_by_handle: dict[object, int] = {}

# --- Durable resume state (survives agent restarts) -------------------------
# Sidecar JSON {id, magnet, savePath} + libtorrent fast-resume blob per torrent
# live on a persistent volume so downloads resume instantly after a restart.
STATE_DIR = os.getenv("STATE_DIR", "state")
os.makedirs(STATE_DIR, exist_ok=True)


def _meta_path(tid: int) -> str:
    return os.path.join(STATE_DIR, f"{tid}.json")


def _resume_path(tid: int) -> str:
    return os.path.join(STATE_DIR, f"{tid}.resume")


def _write_meta(tid: int, magnet: str, save_path: str,
                skip_parts: list[str] | None = None) -> None:
    try:
        with open(_meta_path(tid), "w") as f:
            json.dump({"id": tid, "magnet": magnet, "savePath": save_path,
                       "skipParts": skip_parts or []}, f)
    except OSError as e:
        print(f"[resume] meta write failed for {tid}: {e}")


def _saved_skip_parts(tid: int) -> list[str]:
    try:
        with open(_meta_path(tid)) as f:
            return json.load(f).get("skipParts") or []
    except (OSError, ValueError):
        return []


def _skipped_indices(files, skip_parts: list[str]) -> list[int]:
    """File indices whose name starts with a skipped part (fg-optional-x matches
    fg-optional-x.bin and fg-optional-x-part2.bin)."""
    parts = [p.lower() for p in skip_parts]
    return [i for i in range(files.num_files())
            if any(os.path.basename(files.file_path(i)).lower().startswith(p) for p in parts)]


def _parked_paths(files, indices: list[int]) -> list[tuple[int, str]]:
    """(index, new path) moving each skipped file into a sibling _skipped folder, so the
    repack folder never shows a partial fg-optional-*.bin. Already-parked files stay put."""
    moves = []
    for idx in indices:
        path = files.file_path(idx)
        parent = os.path.dirname(path)
        if os.path.basename(parent) != "_skipped":
            moves.append((idx, os.path.join(parent, "_skipped", os.path.basename(path))))
    return moves


def _delete_state(tid: int) -> None:
    for p in (_meta_path(tid), _resume_path(tid)):
        try:
            os.remove(p)
        except OSError:
            pass


def _request_resume_save(handle) -> None:
    try:
        handle.save_resume_data(lt.save_resume_flags_t.save_info_dict)
    except Exception:
        try:
            handle.save_resume_data()
        except Exception:
            pass


def _alert_pump() -> None:
    """Persist fast-resume blobs as libtorrent emits them."""
    while True:
        _session.wait_for_alert(1000)
        for a in _session.pop_alerts():
            if type(a).__name__ == "save_resume_data_alert":
                try:
                    buf = lt.write_resume_data_buf(a.params)
                    tid = _id_by_handle.get(a.handle)
                    if tid is not None:
                        with open(_resume_path(tid), "wb") as f:
                            f.write(buf)
                except Exception as e:
                    print(f"[resume] blob write failed: {e}")


def _resume_saver() -> None:
    """Periodically snapshot resume data for all live torrents."""
    while True:
        time.sleep(30)
        for handle in list(_id_by_handle.keys()):
            try:
                if handle.is_valid() and handle.has_metadata():
                    _request_resume_save(handle)
            except Exception:
                pass


def _seconds(value) -> int:
    """libtorrent 2.0 reports durations as timedelta, 1.x as plain ints."""
    return int(value.total_seconds()) if hasattr(value, "total_seconds") else int(value or 0)


def _stats_pump() -> None:
    """Swarm stats for every torrent with metadata, finished ones included - the download
    thread exits at 100%, but a seeding torrent still has peers and upload to report."""
    while True:
        time.sleep(2)
        for tid, handle in list(_handle_by_id.items()):
            data = _torrents.get(tid)
            if data is None:
                continue
            try:
                if not handle.is_valid() or not handle.has_metadata():
                    continue
                s = handle.status()
            except Exception:
                continue
            copies = s.distributed_copies
            data.update({
                "seeds": s.num_seeds,
                "connections": s.num_connections,
                "availability": round(copies, 2) if copies >= 0 else None,
                "totalDownloaded": s.all_time_download,
                "totalUploaded": s.all_time_upload,
                "wasted": s.total_failed_bytes + s.total_redundant_bytes,
                "activeSeconds": _seconds(getattr(s, "active_duration", getattr(s, "active_time", 0))),
                "seedingSeconds": _seconds(getattr(s, "seeding_duration", getattr(s, "seeding_time", 0))),
            })
            if data.get("status") == TorrentStatus.SEEDING.value and not handle.is_paused():
                data["uploadSpeed"] = round(s.upload_rate / (1024 * 1024), 4)
                data["peers"] = s.num_peers


threading.Thread(target=_alert_pump, daemon=True).start()
threading.Thread(target=_resume_saver, daemon=True).start()
threading.Thread(target=_stats_pump, daemon=True).start()


def dht_nodes() -> int:
    try:
        return int(_session.status().dht_nodes)
    except Exception:
        return 0


def _peer_ip(peer) -> str | None:
    ip = getattr(peer, "ip", None)
    return ip[0] if isinstance(ip, tuple) else ip


def _bucket_pieces(bits, buckets: int = 256) -> list[float]:
    """Share of each run of pieces already on disk, so the UI draws a fixed-size map
    whether the torrent has 300 pieces or 30 000."""
    n = len(bits)
    if n == 0:
        return []
    out = []
    for b in range(min(buckets, n)):
        lo, hi = b * n // min(buckets, n), (b + 1) * n // min(buckets, n)
        chunk = bits[lo:hi] or bits[lo:lo + 1]
        out.append(round(sum(1 for x in chunk if x) / len(chunk), 2))
    return out


def _trackers(handle) -> list[dict]:
    """libtorrent announces from every local interface (VPN, Hyper-V, loopback), and most
    of those fail by design. A tracker is working if any interface got through."""
    out = []
    try:
        for t in handle.trackers():
            # 2.0 nests per-info-hash state under each endpoint; 1.x keeps it on the endpoint.
            states = [st for ep in (t.get("endpoints") or []) for st in (ep.get("info_hashes") or [ep])]
            ok = [st for st in states if st.get("start_sent") and int(st.get("fails", 0) or 0) == 0]
            tried = [st for st in states if int(st.get("fails", 0) or 0) > 0 or st.get("start_sent")]
            out.append({
                "url": t.get("url", ""),
                "tier": t.get("tier", 0),
                "working": bool(ok),
                "fails": 0 if ok else min((int(st.get("fails", 0) or 0) for st in tried), default=0),
                "updating": any(st.get("updating") for st in states),
                "seeds": max((int(st.get("scrape_complete", -1)) for st in ok), default=-1),
                "leechers": max((int(st.get("scrape_incomplete", -1)) for st in ok), default=-1),
                "message": next((st.get("message") for st in ok + states if st.get("message")), ""),
            })
    except Exception as e:
        print(f"[torrent_service] trackers: {e}")
    return out


def details(torrent_id: int) -> dict:
    """Everything the detail view shows that is too heavy to poll for every torrent."""
    from app.services import geo

    data = _torrents.get(torrent_id)
    handle = _handle_by_id.get(torrent_id)
    if data is None or handle is None or not handle.is_valid():
        raise KeyError(f"Torrent {torrent_id} not found")

    base = {k: v for k, v in data.items() if k != "magnet"}
    if not handle.has_metadata():
        return {**base, "metadata": False, "files": [], "pieces": [], "trackers": _trackers(handle), "peerList": []}

    s = handle.status()
    files = handle.torrent_file().files()
    try:
        progress = handle.file_progress(flags=lt.torrent_handle.piece_granularity)
    except Exception:
        progress = handle.file_progress()
    priorities = handle.get_file_priorities()

    file_rows = []
    for i in range(min(files.num_files(), 500)):
        size = files.file_size(i)
        file_rows.append({
            "path": files.file_path(i),
            "size": size,
            "progress": round(progress[i] / size, 4) if size else 1.0,
            "skipped": i < len(priorities) and priorities[i] == 0,
        })

    peers = []
    try:
        for p in handle.get_peer_info():
            ip = _peer_ip(p)
            peers.append({
                "ip": ip,
                "country": geo.country(ip),
                "client": (p.client.decode("utf-8", "replace") if isinstance(p.client, bytes) else str(p.client))[:40],
                "downSpeed": p.down_speed,
                "upSpeed": p.up_speed,
                "progress": round(p.progress, 3),
                "seed": bool(p.flags & lt.peer_info.seed),
            })
    except Exception as e:
        print(f"[torrent_service] peers {torrent_id}: {e}")
    peers.sort(key=lambda x: x["downSpeed"] + x["upSpeed"], reverse=True)

    return {
        **base,
        "metadata": True,
        "numPieces": s.num_pieces,
        "pieceSize": handle.torrent_file().piece_length(),
        "pieces": _bucket_pieces(list(s.pieces)),
        "files": file_rows,
        "fileCount": files.num_files(),
        "trackers": _trackers(handle),
        "peerList": peers[:60],
        "downloadRate": s.download_rate,
        "uploadRate": s.upload_rate,
        "dhtNodes": dht_nodes(),
    }


def peer_map(torrent_id: int | None = None) -> dict:
    """Peers per country across all torrents, or one."""
    from app.services import geo

    handles = ([(torrent_id, _handle_by_id.get(torrent_id))] if torrent_id is not None
               else list(_handle_by_id.items()))
    countries: dict[str, dict] = {}
    total = unknown = 0
    for tid, handle in handles:
        if handle is None or not handle.is_valid():
            continue
        try:
            peer_list = handle.get_peer_info()
        except Exception:
            continue
        for p in peer_list:
            total += 1
            code = geo.country(_peer_ip(p))
            if not code:
                unknown += 1
                continue
            c = countries.setdefault(code, {"peers": 0, "downSpeed": 0, "upSpeed": 0})
            c["peers"] += 1
            c["downSpeed"] += p.down_speed
            c["upSpeed"] += p.up_speed
    return {
        "geoAvailable": geo.available(),
        "total": total,
        "unknown": unknown,
        "countries": countries,
    }


def add_torrent(torrent_id: int, magnet: str, save_path: str | None = None,
                skip_parts: list[str] | None = None) -> dict:
    # Idempotent: if a live download thread already owns this id, don't start a
    # second one (the reconcile loop may re-request an add we're already running).
    existing = _threads.get(torrent_id)
    if existing is not None and existing.is_alive():
        return _torrents.get(torrent_id, {"id": torrent_id, "magnet": magnet})

    effective_path = _safe_save_path(save_path)
    os.makedirs(effective_path, exist_ok=True)

    # A re-add without the list must not start downloading parts the user skipped.
    if not skip_parts:
        skip_parts = _torrents.get(torrent_id, {}).get("skipParts") or _saved_skip_parts(torrent_id)

    data = {
        "id": torrent_id,
        "magnet": magnet,
        "savePath": effective_path,
        "status": TorrentStatus.DOWNLOADING.value,
        "skipParts": sorted(set(skip_parts or [])),
    }
    _torrents[torrent_id] = data

    t = threading.Thread(target=_download_thread, args=(data,), daemon=True)
    _threads[torrent_id] = t
    t.start()

    return data


def get_all() -> list[dict]:
    return list(_torrents.values())


def _halt(torrent_id: int, status: str) -> None:
    """Pause/stop a torrent. Tolerant: if the engine doesn't hold it (e.g. lost
    after a restart), just record the requested state instead of erroring."""
    handle = _handle_by_id.get(torrent_id)
    if handle is not None:
        handle.auto_managed(False)
        handle.pause()
    if torrent_id in _torrents:
        _torrents[torrent_id]["status"] = status
        _torrents[torrent_id]["downloadSpeed"] = 0.0
        _torrents[torrent_id]["uploadSpeed"] = 0.0
        _torrents[torrent_id]["eta"] = None


def pause(torrent_id: int) -> None:
    _halt(torrent_id, TorrentStatus.PAUSED.value)


def stop(torrent_id: int) -> None:
    _halt(torrent_id, TorrentStatus.STOPPED.value)


def resume(torrent_id: int) -> None:
    # Raises KeyError if the engine lost this torrent - the caller (Java) then
    # falls back to re-adding it from the magnet.
    handle = _get_handle(torrent_id)
    handle.auto_managed(True)
    handle.resume()
    _torrents[torrent_id]["status"] = TorrentStatus.DOWNLOADING.value


def remove(torrent_id: int, delete_files: bool = False) -> None:
    info_hash = _hash_by_id.pop(torrent_id, None)
    if info_hash is not None:
        _handles.pop(info_hash, None)
    handle = _handle_by_id.pop(torrent_id, None)
    if handle is not None:
        _id_by_handle.pop(handle, None)
        try:
            handle.pause()
            if delete_files:
                # Wipe the downloaded payload from disk too, not just the session.
                flag = getattr(lt, "session", None)
                opt = getattr(flag, "delete_files", None) if flag else None
                _session.remove_torrent(handle, opt if opt is not None else 1)
            else:
                _session.remove_torrent(handle)
        except Exception as e:
            print(f"[torrent_service] remove {torrent_id}: {e}")

    _threads.pop(torrent_id, None)
    _torrents.pop(torrent_id, None)
    _delete_state(torrent_id)


def get_storage_info() -> dict:
    usage = shutil.disk_usage(DEFAULT_SAVE_PATH)
    return {
        "Used": usage.used,
        "Total": usage.total,
        "Available": usage.free,
        "Device": _device_label(DEFAULT_SAVE_PATH),
    }


def get_disks() -> list[dict]:
    seen: set[str] = set()
    disks: list[dict] = []

    def _add(path: str, label: str) -> None:
        if not os.path.isdir(path) or _norm(path) in seen:
            return
        try:
            usage = psutil.disk_usage(path)
        except (PermissionError, OSError):
            return
        seen.add(_norm(path))
        disks.append({
            "path": path,
            "device": label,
            "total": round(usage.total / (1024 ** 3), 2),
            "used": round(usage.used / (1024 ** 3), 2),
            "available": round(usage.free / (1024 ** 3), 2),
        })

    for root, label in _READ_ROOTS:
        _add(root, label)

    if not disks:
        _add(DEFAULT_SAVE_PATH, "downloads")

    return disks


def _get_handle(torrent_id: int) -> lt.torrent_handle:
    handle = _handle_by_id.get(torrent_id)
    if handle is None:
        raise KeyError(f"Torrent {torrent_id} not found")
    return handle


def _download_thread(data: dict) -> None:
    torrent_id = data["id"]
    save_path = data.get("savePath", DEFAULT_SAVE_PATH)
    try:
        handle = None
        resume_file = _resume_path(torrent_id)
        if os.path.exists(resume_file):
            # Fast-resume: re-add from saved state, skipping a full re-check.
            try:
                with open(resume_file, "rb") as f:
                    atp = lt.read_resume_data(f.read())
                atp.save_path = save_path
                handle = _session.add_torrent(atp)
                print(f"[resume] fast-resumed torrent {torrent_id}")
            except Exception as e:
                print(f"[resume] bad resume blob for {torrent_id}, re-adding from magnet: {e}")
                handle = None
        if handle is None:
            params = _magnet_params(data["magnet"])
            params.save_path = save_path
            params.storage_mode = lt.storage_mode_t.storage_mode_sparse
            handle = _session.add_torrent(params)
        # Magnets are added paused + auto-managed; taking it out of the queue without
        # resuming left it paused forever (no announce, no metadata) unless the queue
        # happened to start it first.
        handle.auto_managed(False)
        handle.resume()
        _id_by_handle[handle] = torrent_id
        _handle_by_id[torrent_id] = handle
        skip_parts = data.get("skipParts") or []
        _write_meta(torrent_id, data["magnet"], save_path, skip_parts)
        data["status"] = TorrentStatus.DOWNLOADING.value
        data.pop("error", None)

        while not handle.has_metadata():
            if torrent_id not in _torrents:
                _cleanup_handle(handle)
                return
            time.sleep(2)

        info = handle.torrent_file()
        info_hash = info.info_hash()
        files = info.files()

        # Priority 0 means libtorrent never requests those pieces, so deselected
        # optional content costs no bandwidth and no disk.
        excluded = _skipped_indices(files, skip_parts)
        if excluded:
            priorities = [4] * files.num_files()
            for idx in excluded:
                if 0 <= idx < len(priorities):
                    priorities[idx] = 0
            handle.prioritize_files(priorities)
            # Pieces straddle file boundaries, so libtorrent still writes the edges of a
            # skipped file. A partial fg-optional-*.bin beside setup.exe would fail the MD5
            # check and make the installer unpack a corrupt component - park it out of sight.
            for idx, hidden in _parked_paths(files, excluded):
                handle.rename_file(idx, hidden)
            print(f"[torrent_service] {torrent_id}: skipping {len(excluded)} optional file(s)")

        skipped = set(excluded)
        total_size = sum(
            files.file_size(i) for i in range(files.num_files()) if i not in skipped
        )

        problem = _validate_metadata(info, files, total_size, save_path)
        if problem:
            print(f"[torrent_service] Rejected {torrent_id}: {problem}")
            data["status"] = TorrentStatus.ERROR.value
            data["error"] = problem
            _handle_by_id.pop(torrent_id, None)
            _cleanup_handle(handle)
            return

        _handles[info_hash] = handle
        _hash_by_id[torrent_id] = info_hash

        data.update({
            "title": info.name(),
            "size": _format_bytes(total_size),
            "progress": 0.0,
            "peers": 0,
            "downloadSpeed": 0.0,
            "uploadSpeed": 0.0,
        })

        # With skipped files the torrent ends in `finished`, never `seeding` (it does not
        # hold every piece) - waiting for seeding alone would loop forever.
        done_states = (lt.torrent_status.seeding, lt.torrent_status.finished)
        while handle.status().state not in done_states:
            if torrent_id not in _torrents:
                _cleanup_handle(handle)
                return
            if handle.is_paused():
                time.sleep(2)
                continue

            s = handle.status()
            remaining = s.total_wanted - s.total_wanted_done
            eta = _format_eta(remaining / s.download_rate) if (s.download_rate > 0 and remaining > 0) else None
            data.update({
                "progress": round(s.progress * 100, 2),
                "peers": s.num_peers,
                "downloadSpeed": round(s.download_rate / (1024 * 1024), 4),
                "uploadSpeed": round(s.upload_rate / (1024 * 1024), 4),
                "eta": eta,
            })
            time.sleep(2)

        if torrent_id in _torrents:
            data.update({
                "progress": 100.0,
                "status": TorrentStatus.SEEDING.value,
                "downloadSpeed": 0.0,
                "uploadSpeed": 0.0,
                "peers": 0,
                "eta": None,
            })
            _request_resume_save(handle)  # persist completed state

    except Exception as e:
        if torrent_id in _torrents:
            _torrents[torrent_id]["status"] = TorrentStatus.ERROR.value
            _torrents[torrent_id]["error"] = str(e)[:500]
        print(f"[torrent_service] Error on torrent {torrent_id}: {e}")


def _validate_metadata(info, files, total_size: int, save_path: str) -> str | None:
    """None if the torrent is acceptable, else a reason the dashboard can show."""
    try:
        if info.num_files() > MAX_FILES:
            return f"Too many files ({info.num_files()} > {MAX_FILES})"

        if total_size > MAX_TOTAL_BYTES:
            return f"Torrent too large ({_format_bytes(total_size)})"

        free = shutil.disk_usage(save_path).free
        if free < total_size:
            return (f"Not enough disk space: needs {_format_bytes(total_size)}, "
                    f"{_format_bytes(free)} free")

        # _within compares whole path segments, so a drive-root save path works too.
        root = [_prefix(save_path)]
        for i in range(files.num_files()):
            if not _within(os.path.join(save_path, files.file_path(i)), root):
                return f"File path escapes the download folder: {files.file_path(i)}"

        return None
    except Exception as e:
        return f"Metadata validation error: {e}"


def _cleanup_handle(handle: lt.torrent_handle) -> None:
    try:
        handle.pause()
        _session.remove_torrent(handle)
    except Exception:
        pass


def _format_bytes(n: int) -> str:
    for unit in ("B", "KB", "MB", "GB", "TB"):
        if n < 1024.0:
            return f"{n:.1f} {unit}"
        n /= 1024.0
    return f"{n:.1f} PB"


def _format_eta(seconds: float) -> str | None:
    seconds = int(seconds)
    if seconds <= 0:
        return None
    d, rem = divmod(seconds, 86400)
    h, rem = divmod(rem, 3600)
    m, s = divmod(rem, 60)
    if d:
        return f"{d}d {h}h"
    if h:
        return f"{h}h {m}m"
    if m:
        return f"{m}m {s}s"
    return f"{s}s"


def _resume_persisted() -> None:
    """On startup, re-add every torrent recorded on the persistent volume."""
    if not os.path.isdir(STATE_DIR):
        return
    for fn in sorted(os.listdir(STATE_DIR)):
        if not fn.endswith(".json"):
            continue
        try:
            with open(os.path.join(STATE_DIR, fn)) as f:
                meta = json.load(f)
            tid = int(meta["id"])
            if tid in _torrents:
                continue
            add_torrent(tid, meta["magnet"], meta.get("savePath"),
                        meta.get("skipParts"))
            print(f"[resume] restored torrent {tid}")
        except Exception as e:
            print(f"[resume] failed to restore {fn}: {e}")


_resume_persisted()
