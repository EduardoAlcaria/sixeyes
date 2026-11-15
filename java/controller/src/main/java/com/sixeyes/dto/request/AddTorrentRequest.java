package com.sixeyes.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AddTorrentRequest(

        @NotBlank(message = "Magnet link is required")
        @Pattern(regexp = "magnet:\\?xt=urn:btih:.*", message = "Must be a valid magnet link (magnet:?xt=urn:btih:...)")
        @Size(max = 4000, message = "Magnet link exceeds maximum length")
        String magnet,

        @Size(max = 4000, message = "Download path exceeds maximum length")
        String downloadPath,

        @Size(max = 4000, message = "Install path exceeds maximum length")
        String installPath,

        Boolean autoInstall,

        /**
         * FitGirl optional parts to skip, by name (fg-optional-bonus-content). The engine
         * matches them against the torrent's files once metadata arrives, so the dialog
         * does not have to wait on peers for file indices.
         */
        @Size(max = 50)
        List<@Pattern(regexp = "fg-optional-[a-z0-9-]+", message = "Invalid optional part") String> skipParts

) {}
