package com.sixeyes.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MkdirRequest(

        @NotBlank(message = "Parent path is required")
        @Size(max = 4000)
        String parent,

        @NotBlank(message = "Folder name is required")
        @Size(max = 255)
        String name

) {}
