package com.sixeyes.exception;

public class DuplicateMagnetException extends RuntimeException {
    public DuplicateMagnetException() {
        super("A torrent with this magnet link is already being tracked");
    }
}
