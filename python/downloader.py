from flask import Flask, request, jsonify

import libtorrent as lt
import time
import os
import threading
from flask_cors import CORS
import shutil
from pathlib import Path

from TorrentEnum import Status

app = Flask(__name__)
CORS(app)

ses = lt.session()
ses.listen_on(6881, 6891)

save_path = "downloads"
os.makedirs(save_path, exist_ok=True)


torrentsByID = {}
torrentHandles = {}
torrentInfoHash = {}
torrentThreads = {}


def download_torrent_thread(torrent_data):
    torrent_id = torrent_data['id']

    try:
        handle = lt.add_magnet_uri(ses, torrent_data['magnet'], {
            'save_path': save_path,
            'storage_mode': lt.storage_mode_t.storage_mode_sparse,
        })

        handle.auto_managed(False)


        torrent_data['status'] = Status.DOWNLOADING.value

        while not handle.has_metadata():
            if torrent_id not in torrentsByID:
                handle.pause()
                ses.remove_torrent(handle)
                return
            time.sleep(1)

        info = handle.torrent_file()
        info_hash = info.info_hash()

        total_size = 0
        for f in info.files():
            total_size += f.size

        torrentHandles[info_hash] = handle
        torrentInfoHash[torrent_id] = info_hash


        torrent_data.update({
            'title': handle.torrent_file().name(),
            'progress': 0,
            'peers': 0,
            'downloadSpeed': 0,
            'uploadSpeed': 0,
            'status': Status.DOWNLOADING.value,
            'size': total_size
        })

        while handle.status().state != lt.torrent_status.seeding:
            if torrent_id not in torrentsByID:
                handle.pause()
                ses.remove_torrent(handle)
                return

            if handle.is_paused():
                time.sleep(1)
                continue

            s = handle.status()

            torrent_data.update({
                'progress': s.progress * 100,
                'peers': s.num_peers,
                'downloadSpeed': s.download_rate / (1024 * 1024),
                'uploadSpeed': s.upload_rate / (1024 * 1024),
            })
            time.sleep(1)
            print(f"Torrent {torrent_id} - Downloading speed: {torrent_data['downloadSpeed']:.2f} MB/s")
            print(f"Torrent {torrent_id} - Uploading speed: {torrent_data['uploadSpeed']:.2f} MB/s")

        if torrent_id in torrentsByID:
            torrent_data.update({
                'progress': 100,
                'status': Status.SEEDING.value,
            })
            print(f"Download finished: {torrent_data['title']}")

    except Exception as e:
        print(f"Error downloading torrent {torrent_id}: {str(e)}")
        if torrent_id in torrentsByID:
            torrent_data['status'] = "ERROR"


@app.route('/python/add', methods=['POST'])
def start_download():
    torrent_data = request.get_json()
    torrent_id = torrent_data['id']


    torrentsByID[torrent_id] = torrent_data

    thread = threading.Thread(target=download_torrent_thread, args=(torrent_data,))
    thread.daemon = True
    thread.start()


    torrentThreads[torrent_id] = thread

    return jsonify(torrent_data)

@app.route('/python/get', methods=['GET'])
def get_status():
    return jsonify(list(torrentsByID.values()))


@app.route('/python/pause', methods=['PUT'])
def pause_torrent():
    res = request.get_json()
    torrent_id = res['id']

    if torrent_id not in torrentInfoHash:
        return jsonify({"error": "Torrent not found"}), 404

    torrent_info_hash = torrentInfoHash[torrent_id]
    handle = torrentHandles.get(torrent_info_hash)

    if handle:
        handle.auto_managed(False)
        handle.pause()
        torrentsByID[torrent_id]['status'] = Status.PAUSED.value
        return jsonify({"success": True}), 200
    else:
        return jsonify({"error": "Torrent handle not found"}), 500


@app.route('/python/resume', methods=['PUT'])
def resume_torrent():
    res = request.get_json()
    torrent_id = res['id']

    if torrent_id not in torrentInfoHash:
        return jsonify({"error": "Torrent not found"}), 404

    torrent_info_hash = torrentInfoHash[torrent_id]
    handle = torrentHandles.get(torrent_info_hash)

    if handle:
        handle.auto_managed(True)
        handle.resume()
        torrentsByID[torrent_id]['status'] = Status.DOWNLOADING.value
        return jsonify({"success": True}), 200
    else:
        return jsonify({"error": "Torrent handle not found"}), 500


@app.route('/python/remove', methods=['DELETE'])
def remove_torrent():
    res = request.get_json()
    torrent_id = res['id']

    if torrent_id not in torrentsByID:
        return jsonify({"error": "Torrent not found"}), 404

    try:

        if torrent_id in torrentInfoHash:
            torrent_info_hash = torrentInfoHash[torrent_id]


            if torrent_info_hash in torrentHandles:
                handle = torrentHandles[torrent_info_hash]
                handle.pause()
                ses.remove_torrent(handle)
                del torrentHandles[torrent_info_hash]

            del torrentInfoHash[torrent_id]


        if torrent_id in torrentThreads:
            del torrentThreads[torrent_id]


        del torrentsByID[torrent_id]

        return jsonify({"success": True, "message": "Torrent removed successfully"}), 200

    except Exception as e:
        return jsonify({"error": f"Failed to remove torrent: {str(e)}"}), 500



@app.route('/python/systemInfo/getStorageInfo', methods=['GET'])
def get_used_storage():
    try:
        drive = Path(__file__).anchor
        driveInfo = shutil.disk_usage(drive)

        return jsonify({"Used" : driveInfo.used, "Available" : driveInfo.free})

    except Exception as e:
        print(e)

if __name__ == "__main__":
    app.run(port=9999, debug=True)