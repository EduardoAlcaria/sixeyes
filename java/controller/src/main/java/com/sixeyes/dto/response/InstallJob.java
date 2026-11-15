package com.sixeyes.dto.response;

/**
 * A pending install: torrent id, title, where its files live, where to install, and
 * whether it is a FitGirl repack (the only kind that may run).
 */
public record InstallJob(Long id, String title, String savePath, String installPath, boolean fitgirl) {}
