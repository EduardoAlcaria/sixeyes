package com.sixeyes.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddTorrentRequest(

        @NotBlank(message = "Magnet link is required")
        @Pattern(regexp = "magnet:\\?xt=urn:btih:.*", message = "Must be a valid magnet link (magnet:?xt=urn:btih:...)")
        @Size(max = 4000, message = "Magnet link exceeds maximum length")
        String magnet,

        // Optional host save path chosen via the folder picker; null -> Settings default.
        @Size(max = 4000, message = "Download path exceeds maximum length")
        String downloadPath

) {}
