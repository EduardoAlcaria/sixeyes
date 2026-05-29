package com.sixeyes.exception;

public class InvalidMagnetException extends RuntimeException {
    public InvalidMagnetException(String magnet) {
        super("Invalid magnet link: " + (magnet != null ? magnet.substring(0, Math.min(magnet.length(), 50)) : "null"));
    }
}
