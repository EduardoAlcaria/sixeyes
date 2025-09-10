from enum import Enum

class Status(Enum):
    DOWNLOADING = "Downloading"
    SEEDING = "Seeding"
    PAUSED = "Paused"
    STOPPED = "Stopped"
