package com.sixeyes.dto.response;

/** A pending install the host watcher should execute: torrent id, title and its save path. */
public record InstallJob(Long id, String title, String savePath) {}
