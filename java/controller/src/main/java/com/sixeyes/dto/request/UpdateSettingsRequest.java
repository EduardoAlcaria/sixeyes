package com.sixeyes.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateSettingsRequest(
        @NotBlank(message = "Download path is required") String downloadPath
) {}
