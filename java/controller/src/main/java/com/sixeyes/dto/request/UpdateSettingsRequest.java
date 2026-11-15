package com.sixeyes.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateSettingsRequest(
        @NotBlank(message = "Download path is required") @Size(max = 4000) String downloadPath,
        @Size(max = 4000) String installPath
) {}
