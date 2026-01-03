package com.sixeyes.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Host watcher reports install progress/result back to the API. */
public record InstallStatusRequest(

        @NotBlank(message = "status is required")
        String status,        // INSTALLING | INSTALLED | FAILED

        @Size(max = 2000)
        String message

) {}
