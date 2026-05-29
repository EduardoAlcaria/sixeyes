from enum import Enum


class TorrentStatus(Enum):
    DOWNLOADING = "Downloading"
    SEEDING = "Seeding"
    PAUSED = "Paused"
    STOPPED = "Stopped"
    ERROR = "Error"
