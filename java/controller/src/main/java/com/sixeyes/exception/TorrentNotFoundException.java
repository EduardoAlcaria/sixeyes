package com.sixeyes.exception;

public class TorrentNotFoundException extends RuntimeException {
    public TorrentNotFoundException(Long id) {
        super("Torrent not found with id: " + id);
    }
}
