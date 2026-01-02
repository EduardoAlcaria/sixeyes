import os
import threading
import time

import libtorrent as lt
import psutil

from app.domain.status import TorrentStatus

DEFAULT_SAVE_PATH = os.getenv("DOWNLOAD_PATH", "downloads")
os.makedirs(DEFAULT_SAVE_PATH, exist_ok=True)

_session = lt.session()
_session.listen_on(6881, 6891)

_torrents: dict[int, dict] = {}
_handles: dict[object, lt.torrent_handle] = {}
_hash_by_id: dict[int, object] = {}
_threads: dict[int, threading.Thread] = {}


def add_torrent(torrent_id: int, magnet: str, save_path: str | None = None) -> dict:
    effective_path = save_path or DEFAULT_SAVE_PATH
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
            handle.pause()
            _session.remove_torrent(handle)
        del _hash_by_id[torrent_id]

    _threads.pop(torrent_id, None)
    _torrents.pop(torrent_id, None)


def get_storage_info() -> dict:
    import shutil
    usage = shutil.disk_usage(DEFAULT_SAVE_PATH)
    return {"Used": usage.used, "Total": usage.total, "Available": usage.free}


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
        handle = lt.add_magnet_uri(_session, data["magnet"], {
            "save_path": save_path,
            "storage_mode": lt.storage_mode_t.storage_mode_sparse,
        })
        handle.auto_managed(False)
        data["status"] = TorrentStatus.DOWNLOADING.value

        while not handle.has_metadata():
            if torrent_id not in _torrents:
                _cleanup_handle(handle)
                return
            time.sleep(2)

        info = handle.torrent_file()
        info_hash = info.info_hash()
        total_size = sum(f.size for f in info.files())

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
            data.update({
                "progress": round(s.progress * 100, 2),
                "peers": s.num_peers,
                "downloadSpeed": round(s.download_rate / (1024 * 1024), 4),
                "uploadSpeed": round(s.upload_rate / (1024 * 1024), 4),
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

    except Exception as e:
        if torrent_id in _torrents:
            _torrents[torrent_id]["status"] = TorrentStatus.ERROR.value
        print(f"[torrent_service] Error on torrent {torrent_id}: {e}")


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
