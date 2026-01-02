"""Tests for magnet parsing and save_path traversal guards.

The vendored libtorrent in this environment is a cp313 binary that cannot be
imported under the installed Python (ABI mismatch), so ``import libtorrent``
yields an empty namespace package. ``app.services.torrent_service`` calls
``lt.session()`` at import time, which would crash collection.

To keep the path-traversal guards testable regardless of libtorrent
availability, we detect whether libtorrent is functional. If it is not, we
install a minimal stub that provides just enough for the module to import, and
skip the tests that genuinely exercise libtorrent's parsing.
"""
import sys
import types

import pytest

import libtorrent as _lt

LIBTORRENT_OK = hasattr(_lt, "session") and hasattr(_lt, "torrent_info")

if not LIBTORRENT_OK:
    # Minimal stub so app.services.torrent_service can be imported. Only the
    # symbols touched at module-load time need to exist.
    _stub = types.ModuleType("libtorrent")

    class _Session:
        def listen_on(self, *a, **k):
            pass

    _stub.session = _Session

    class _StorageMode:
        storage_mode_sparse = 0

    _stub.storage_mode_t = _StorageMode
    _stub.torrent_handle = object
    sys.modules["libtorrent"] = _stub

from app.services import torrent_service  # noqa: E402


def _make_torrent_bytes(tmp_path) -> bytes:
    import libtorrent as lt

    f = tmp_path / "hello.txt"
    f.write_text("hello sixeyes")
    fs = lt.file_storage()
    lt.add_files(fs, str(f))
    t = lt.create_torrent(fs)
    t.add_tracker("udp://tracker.example.com:80")
    lt.set_piece_hashes(t, str(tmp_path))
    return lt.bencode(t.generate())


@pytest.mark.skipif(not LIBTORRENT_OK, reason="libtorrent not functional in this environment")
def test_magnet_from_torrent_bytes(tmp_path):
    data = _make_torrent_bytes(tmp_path)
    magnet = torrent_service.magnet_from_torrent_bytes(data)
    assert magnet.startswith("magnet:?xt=urn:btih:")


@pytest.mark.skipif(not LIBTORRENT_OK, reason="libtorrent not functional in this environment")
def test_invalid_bytes_raise():
    with pytest.raises(Exception):
        torrent_service.magnet_from_torrent_bytes(b"not a torrent")


def test_safe_save_path_rejects_traversal():
    with pytest.raises(ValueError):
        torrent_service._safe_save_path("/etc/passwd")
    with pytest.raises(ValueError):
        torrent_service._safe_save_path(torrent_service._SAVE_ROOT + "/../escape")


def test_safe_save_path_allows_none_and_subdir():
    assert torrent_service._safe_save_path(None) == torrent_service._SAVE_ROOT
