from flask import Flask, request, jsonify

import libtorrent as lt
import time
import os
import threading
from flask_cors import CORS

from TorrentEnum import Status

app = Flask(__name__)
CORS(app)

ses = lt.session()
ses.listen_on(6881, 6891)

save_path = "downloads"
os.makedirs(save_path, exist_ok=True)

torrentG = dict()
torrentHandles = {}
torrentInfoHash = {}


def download_torrent_thread(torrentG):
    handle = lt.add_magnet_uri(ses, torrentG['magnet'], {
        'save_path': save_path,
        'storage_mode': lt.storage_mode_t.storage_mode_sparse,
    })

    handle.auto_managed(False)
    while not handle.has_metadata():
        time.sleep(1)
    info = handle.torrent_file()

    total_size = 0
    for f in info.files():
        total_size += f.size

    torrentHandles[info.info_hash()] = handle
    torrentG['title'] = handle.torrent_file().name()
    torrentInfoHash[torrentG['id']] = info.info_hash()
    torrentG['progress'] = 0
    torrentG['peers'] = 0
    torrentG['downloadSpeed'] = 0
    torrentG['uploadSpeed'] = 0
    torrentG['status'] = Status.DOWNLOADING.value
    torrentG['size'] = total_size

    while handle.status().state != lt.torrent_status.seeding:
        if handle.is_paused():
            time.sleep(1)
            continue
        s = handle.status()

        torrentG.update({
            'progress': s.progress * 100,
            'peers': s.num_peers,
            'downloadSpeed': s.download_rate / (1024 * 1024),
            'uploadSpeed': s.upload_rate / (1024 * 1024),
        })
        time.sleep(1)
        print("Downloading speed", torrentG['downloadSpeed'])
        print("Uploading speed", torrentG['uploadSpeed'])

    torrentG.update({
        'progress': 100,
        'status': Status.SEEDING.value,
    })

    print(f"Download finished: {torrentG['title']}")


@app.route('/python/downloadTorrent', methods=['POST'])
def start_download():
    global torrentG
    torrentG = request.get_json()

    thread = threading.Thread(target=download_torrent_thread, args=(torrentG,))
    thread.start()

    return jsonify(torrentG)


@app.route('/python/getTorrentStats', methods=['GET'])
def get_status():
    return jsonify(torrentG)


@app.route('/python/pausedTorrent', methods=['PUT'])
def pause_torrent():
    res = request.get_json()
    torrent_infoHash = torrentInfoHash[res['id']]
    handle = torrentHandles[torrent_infoHash]

    if handle:
        handle.auto_managed(False)
        handle.pause()
        torrentG['status'] = Status.PAUSED.value
        return jsonify({"success": True}), 200
    else:
        return jsonify({"error": "Torrent not paused"}), 500


@app.route('/python/resumeTorrent', methods=['PUT'])
def resume_torrent():
    res = request.get_json()
    torrent_infoHash = torrentInfoHash[res['id']]
    handle = torrentHandles[torrent_infoHash]

    if handle:
        handle.auto_managed(True)
        handle.resume()
        torrentG['status'] = Status.DOWNLOADING.value
        return jsonify({"success": True}), 200
    else:
        return jsonify({"error": "Torrent not resumed"}), 500


if __name__ == "__main__":
    app.run(port=9999)
