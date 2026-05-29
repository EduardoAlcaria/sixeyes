package com.sixeyes.dto.response;

import com.sixeyes.model.Settings;

public record SettingsResponse(String downloadPath) {

    public static SettingsResponse from(Settings s) {
        return new SettingsResponse(s.getDownloadPath());
    }
}
