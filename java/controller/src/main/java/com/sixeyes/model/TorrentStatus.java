package com.sixeyes.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TorrentStatus {
    DOWNLOADING("Downloading"),
    SEEDING("Seeding"),
    PAUSED("Paused"),
    STOPPED("Stopped"),
    ERROR("Error");

    private final String value;

    TorrentStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static TorrentStatus fromValue(String value) {

        if (value == null || value.isBlank()) {
            return DOWNLOADING;
        }

        for (TorrentStatus status : values()) {

            if (status.value.equalsIgnoreCase(value.trim())){
                return status;
            }

        }

        return DOWNLOADING;
    }
}
