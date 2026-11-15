"""IP -> country for the peer map, from a local DB-IP "IP to Country Lite" database.

Lookups stay on this machine: sending every peer IP to a web API would leak the swarm
membership. The database is optional - without it the map simply reports no countries.
Fetch or refresh it with scripts/geoip-update.ps1. DB-IP Lite is CC BY 4.0; the map
credits it.
"""
import ipaddress
import os
import threading

GEOIP_DB = os.getenv("GEOIP_DB", os.path.join(os.getenv("STATE_DIR", "state"), "dbip-country-lite.mmdb"))

_reader = None
_loaded_mtime = None
_lock = threading.Lock()


def _get_reader():
    """Opens the database lazily and reopens it when the update script replaces it."""
    global _reader, _loaded_mtime
    try:
        mtime = os.path.getmtime(GEOIP_DB)
    except OSError:
        return None
    with _lock:
        if _reader is None or mtime != _loaded_mtime:
            try:
                import maxminddb
                _reader = maxminddb.open_database(GEOIP_DB)
                _loaded_mtime = mtime
            except Exception as e:
                print(f"[geo] cannot open {GEOIP_DB}: {e}")
                _reader = None
        return _reader


def available() -> bool:
    return _get_reader() is not None


def country(ip: str | None) -> str | None:
    """ISO 3166 alpha-2 code, or None for private, unknown or unparseable addresses."""
    if not ip:
        return None
    try:
        if ipaddress.ip_address(ip).is_private:
            return None
    except ValueError:
        return None
    reader = _get_reader()
    if reader is None:
        return None
    try:
        rec = reader.get(ip)
    except Exception:
        return None
    if not rec:
        return None
    return (rec.get("country") or {}).get("iso_code")
