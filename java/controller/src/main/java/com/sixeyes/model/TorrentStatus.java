package com.sixeyes.model;

public enum TorrentStatus {
    PAUSED("Paused"),
    DOWNLOADING("Downloading"),
    SEEDING("Seeding"),
    STOPPED("Stopped");

    private final String value;

    TorrentStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
