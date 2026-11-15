import os

import pytest

lt = pytest.importorskip("libtorrent")
if not hasattr(lt, "session"):
    pytest.skip("libtorrent not functional here", allow_module_level=True)

os.environ.setdefault("AGENT_KEY", "test")
from app.services import torrent_service as ts  # noqa: E402


class _Files:
    def __init__(self, paths):
        self._paths = paths

    def num_files(self):
        return len(self._paths)

    def file_path(self, i):
        return self._paths[i]


def test_skip_parts_match_by_name_prefix():
    files = _Files([
        os.path.join("Game", "setup.exe"),
        os.path.join("Game", "fg-01.bin"),
        os.path.join("Game", "fg-optional-bonus-content.bin"),
        os.path.join("Game", "fg-optional-russian-part2.bin"),
    ])
    assert ts._skipped_indices(files, ["fg-optional-bonus-content", "fg-optional-russian"]) == [2, 3]
    assert ts._skipped_indices(files, []) == []


def test_skipped_parts_are_parked_out_of_the_repack_folder_once():
    files = _Files([
        os.path.join("Game", "fg-01.bin"),
        os.path.join("Game", "fg-optional-8k-backgrounds.bin"),
        os.path.join("Game", "_skipped", "fg-optional-bonus.bin"),
    ])
    assert ts._parked_paths(files, [1, 2]) == [
        (1, os.path.join("Game", "_skipped", "fg-optional-8k-backgrounds.bin")),
    ]


class _Info:
    def num_files(self):
        return 1


def test_drive_root_save_path_is_not_rejected():
    root = os.path.splitdrive(os.getcwd())[0] + os.sep
    files = _Files([os.path.join("Game", "setup.exe")])
    assert ts._validate_metadata(_Info(), files, 1, root) is None


def test_path_escape_is_rejected(tmp_path):
    files = _Files([os.path.join("..", "evil.exe")])
    assert "escapes" in ts._validate_metadata(_Info(), files, 1, str(tmp_path))
