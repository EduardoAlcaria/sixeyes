import os

import pytest

lt = pytest.importorskip("libtorrent")
if not hasattr(lt, "session"):
    pytest.skip("libtorrent not functional here", allow_module_level=True)

os.environ.setdefault("AGENT_KEY", "test")
from app.services import games, geo, steam  # noqa: E402
from app.services import torrent_service as ts  # noqa: E402


def test_vdf_parses_nested_blocks_and_escapes():
    text = '''
    "AppState"
    {
        "appid"   "570"
        "name"    "Dota \\"2\\""
        // comment line
        "UserConfig" { "language" "english" }
    }'''
    app = steam.parse_vdf(text)["AppState"]
    assert app["appid"] == "570"
    assert app["name"] == 'Dota "2"'
    assert app["UserConfig"]["language"] == "english"


def test_vdf_lookup_ignores_key_case():
    tree = steam.parse_vdf('"Software" { "valve" { "Steam" { "Apps" { "10" { "Playtime" "42" } } } } }')
    apps = steam._get(steam._get(steam._get(steam._get(tree, "software"), "Valve"), "steam"), "apps")
    assert apps["10"]["Playtime"] == "42"


def test_pieces_are_bucketed_to_a_fixed_width():
    bits = [True] * 600 + [False] * 400
    buckets = ts._bucket_pieces(bits, 10)
    assert len(buckets) == 10
    assert buckets[0] == 1.0 and buckets[-1] == 0.0
    assert ts._bucket_pieces([], 10) == []
    assert ts._bucket_pieces([True, False], 256) == [1.0, 0.0]


def test_find_exe_prefers_the_game_over_installers(tmp_path):
    root = tmp_path / "Games"
    game = root / "Hades II [FitGirl Repack]"
    (game / "_Redist").mkdir(parents=True)
    (game / "unins000.exe").write_bytes(b"x" * 5000)
    (game / "_Redist" / "vc_redist.x64.exe").write_bytes(b"x" * 9000)
    (game / "CrashReporter.exe").write_bytes(b"x" * 9000)
    (game / "Hades2.exe").write_bytes(b"x" * 100)
    assert games.find_exe(str(game), [str(root)]).endswith("Hades2.exe")


def test_find_exe_falls_back_to_the_largest(tmp_path):
    root = tmp_path / "Games"
    game = root / "Some Game"
    game.mkdir(parents=True)
    (game / "small.exe").write_bytes(b"x" * 10)
    (game / "big.exe").write_bytes(b"x" * 1000)
    assert games.find_exe(str(game), [str(root)]).endswith("big.exe")


def test_exe_outside_the_game_roots_is_refused(tmp_path):
    root = tmp_path / "Games"
    root.mkdir()
    outside = tmp_path / "evil.exe"
    outside.write_bytes(b"x")
    with pytest.raises(ValueError):
        games.launch(str(outside), [str(root)])
    with pytest.raises(ValueError):
        games.find_exe(str(tmp_path), [str(root)])


def test_geo_skips_private_and_garbage_addresses():
    assert geo.country("192.168.0.10") is None
    assert geo.country("not-an-ip") is None
    assert geo.country(None) is None


def test_geo_resolves_a_public_address_when_the_database_exists(monkeypatch):
    db = os.getenv("GEOIP_TEST_DB", r"D:\SixEyes\state\dbip-country-lite.mmdb")
    if not os.path.exists(db):
        pytest.skip("no GeoIP database on this machine")
    monkeypatch.setattr(geo, "GEOIP_DB", db)
    assert geo.country("8.8.8.8") == "US"
