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


def _host_drive_roots() -> list[tuple[str, str]]:
    """[(realpath, label)] for each mounted host drive, e.g. (/host/C, 'C:')."""
    env = os.getenv("HOST_DRIVES", "")
    roots: list[tuple[str, str]] = []
    for p in [x.strip() for x in env.split(",") if x.strip()]:
        label = p.rstrip("/").split("/")[-1] + ":"
        roots.append((os.path.realpath(p), label))
    return roots


_HOST_ROOTS = _host_drive_roots()
# A save path is allowed anywhere under a mounted drive or the default downloads dir.
_ALLOWED_ROOTS = [r for r, _ in _HOST_ROOTS] + [_SAVE_ROOT]


def _is_within_allowed(resolved: str) -> bool:
    for root in _ALLOWED_ROOTS:
        if resolved == root or resolved.startswith(root + os.sep):
            return True
    return False


def _safe_save_path(candidate: str | None) -> str:
    """Resolve candidate under an allowed root (mounted drive or downloads); reject escape."""
    if not candidate:
        return _SAVE_ROOT
    resolved = os.path.realpath(candidate)
    if not _is_within_allowed(resolved):
        raise ValueError(f"save_path not under an allowed root: {candidate}")
    return resolved


def _device_label(path: str) -> str:
    """Which mounted drive (C:/D:) a path physically lives on, by st_dev match."""
    try:
        dev = os.stat(path).st_dev
    except OSError:
        return "downloads"
    for root, label in _HOST_ROOTS:
        try:
            if os.stat(root).st_dev == dev:
                return label
        except OSError:
            continue
    return "downloads"


def list_dir(path: str | None) -> dict:
    """List sub-directories of an allowed path. No path -> the drive roots."""
    if not path:
        entries = [{"name": label, "path": root} for root, label in _HOST_ROOTS]
        entries.append({"name": "downloads", "path": _SAVE_ROOT})
        return {"path": "", "parent": None, "entries": entries}

    resolved = os.path.realpath(path)
    if not _is_within_allowed(resolved):
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
    parent = parent if (parent != resolved and _is_within_allowed(parent)) else None
    return {"path": resolved, "parent": parent, "entries": entries}


def make_dir(parent: str, name: str) -> dict:
    """Create a new sub-directory under an allowed parent."""
    if not name or "/" in name or "\\" in name or name in (".", ".."):
        raise ValueError("invalid folder name")
    base = os.path.realpath(parent)
    if not _is_within_allowed(base):
        raise ValueError("parent not allowed")
    target = os.path.realpath(os.path.join(base, name))
    if not _is_within_allowed(target):
        raise ValueError("target not allowed")
    os.makedirs(target, exist_ok=True)
    return {"path": target}


def magnet_from_torrent_bytes(data: bytes) -> str:
    """Parse raw .torrent file bytes and return a magnet URI."""
    info = lt.torrent_info(lt.bdecode(data))
    return lt.make_magnet_uri(info)

_session = lt.session()
_session.listen_on(6881, 6891)

_torrents: dict[int, dict] = {}
_handles: dict[object, lt.torrent_handle] = {}
_hash_by_id: dict[int, object] = {}
_threads: dict[int, threading.Thread] = {}
_id_by_handle: dict[object, int] = {}

# --- Durable resume state (survives agent restarts) -------------------------
# Sidecar JSON {id, magnet, savePath} + libtorrent fast-resume blob per torrent
# live on a persistent volume so downloads resume instantly after a restart.
STATE_DIR = os.getenv("STATE_DIR", "/app/state")
os.makedirs(STATE_DIR, exist_ok=True)


def _meta_path(tid: int) -> str:
    return os.path.join(STATE_DIR, f"{tid}.json")


def _resume_path(tid: int) -> str:
    return os.path.join(STATE_DIR, f"{tid}.resume")


def _write_meta(tid: int, magnet: str, save_path: str) -> None:
    try:
        with open(_meta_path(tid), "w") as f:
            json.dump({"id": tid, "magnet": magnet, "savePath": save_path}, f)
    except OSError as e:
        print(f"[resume] meta write failed for {tid}: {e}")


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


threading.Thread(target=_alert_pump, daemon=True).start()
threading.Thread(target=_resume_saver, daemon=True).start()


def add_torrent(torrent_id: int, magnet: str, save_path: str | None = None) -> dict:
    effective_path = _safe_save_path(save_path)
    os.makedirs(effective_path, exist_ok=True)

    data = {
        "id": torrent_id,
        "magnet": magnet,
        "savePath": effective_path,
        "status": TorrentStatus.DOWNLOADING.value,
    }
    _torrents[torrent_id] = data

    t = threading.Thread(target=_download_thread, args=(data,), daemon=True)
    _threads[torrent_id] = t
    t.start()

    return data


def get_all() -> list[dict]:
    return list(_torrents.values())


def pause(torrent_id: int) -> None:
    handle = _get_handle(torrent_id)
    handle.auto_managed(False)
    handle.pause()
    _torrents[torrent_id]["status"] = TorrentStatus.PAUSED.value


def resume(torrent_id: int) -> None:
    handle = _get_handle(torrent_id)
    handle.auto_managed(True)
    handle.resume()
    _torrents[torrent_id]["status"] = TorrentStatus.DOWNLOADING.value


def remove(torrent_id: int) -> None:
    if torrent_id in _hash_by_id:
        info_hash = _hash_by_id[torrent_id]
        if info_hash in _handles:
            handle = _handles.pop(info_hash)
            _id_by_handle.pop(handle, None)
            handle.pause()
            _session.remove_torrent(handle)
        del _hash_by_id[torrent_id]

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
    host_drives_env = os.getenv("HOST_DRIVES", "")
    host_drive_paths = [p.strip() for p in host_drives_env.split(",") if p.strip()]

    seen: set[int] = set()
    disks: list[dict] = []

    def _add(path: str, label: str) -> None:
        if not os.path.isdir(path):
            return
        try:
            dev_id = os.stat(path).st_dev
            if dev_id in seen:
                return
            seen.add(dev_id)
            usage = psutil.disk_usage(path)
            disks.append({
                "path": path,
                "device": label,
                "total": round(usage.total / (1024 ** 3), 2),
                "used": round(usage.used / (1024 ** 3), 2),
                "available": round(usage.free / (1024 ** 3), 2),
            })
        except (PermissionError, OSError):
            pass

    for drive_path in host_drive_paths:
        label = drive_path.split("/")[-1] + ":"
        _add(drive_path, label)

    if not disks:
        _add(DEFAULT_SAVE_PATH, "downloads")

    return disks


def _get_handle(torrent_id: int) -> lt.torrent_handle:
    if torrent_id not in _hash_by_id:
        raise KeyError(f"Torrent {torrent_id} not found or metadata not yet received")
    return _handles[_hash_by_id[torrent_id]]


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
            handle = lt.add_magnet_uri(_session, data["magnet"], {
                "save_path": save_path,
                "storage_mode": lt.storage_mode_t.storage_mode_sparse,
            })
        handle.auto_managed(False)
        _id_by_handle[handle] = torrent_id
        _write_meta(torrent_id, data["magnet"], save_path)
        data["status"] = TorrentStatus.DOWNLOADING.value

        while not handle.has_metadata():
            if torrent_id not in _torrents:
                _cleanup_handle(handle)
                return
            time.sleep(2)

        info = handle.torrent_file()
        info_hash = info.info_hash()
        files = info.files()
        total_size = sum(f.size for f in files)

        if not _validate_metadata(info, files, total_size, save_path):
            data["status"] = TorrentStatus.ERROR.value
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

        while handle.status().state != lt.torrent_status.seeding:
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
            })
            _request_resume_save(handle)  # persist completed state

    except Exception as e:
        if torrent_id in _torrents:
            _torrents[torrent_id]["status"] = TorrentStatus.ERROR.value
        print(f"[torrent_service] Error on torrent {torrent_id}: {e}")


def _validate_metadata(info, files, total_size: int, save_path: str) -> bool:
    try:
        if info.num_files() > MAX_FILES:
            print(f"[torrent_service] Rejected: file count {info.num_files()} exceeds MAX_FILES={MAX_FILES}")
            return False

        if total_size > MAX_TOTAL_BYTES:
            print(f"[torrent_service] Rejected: total size {total_size} exceeds MAX_TOTAL_BYTES={MAX_TOTAL_BYTES}")
            return False

        free = shutil.disk_usage(save_path).free
        if free < total_size:
            print(f"[torrent_service] Rejected: insufficient disk space ({free} < {total_size}) at {save_path}")
            return False

        root = os.path.realpath(save_path) + os.sep
        for f in files:
            target = os.path.realpath(os.path.join(save_path, f.path))
            if not target.startswith(root):
                print(f"[torrent_service] Rejected: file path escapes save root: {f.path}")
                return False

        return True
    except Exception as e:
        print(f"[torrent_service] Metadata validation error: {e}")
        return False


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
            add_torrent(tid, meta["magnet"], meta.get("savePath"))
            print(f"[resume] restored torrent {tid}")
        except Exception as e:
            print(f"[resume] failed to restore {fn}: {e}")


_resume_persisted()
