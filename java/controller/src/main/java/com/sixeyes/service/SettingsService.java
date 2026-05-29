package com.sixeyes.service;

import com.sixeyes.dto.request.UpdateSettingsRequest;
import com.sixeyes.dto.response.SettingsResponse;
import com.sixeyes.model.Settings;
import com.sixeyes.repo.SettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class SettingsService {

    private final SettingsRepository settingsRepository;

    @Value("${settings.default-download-path:/app/downloads}")
    private String defaultDownloadPath;

    @Transactional(readOnly = true)
    public SettingsResponse getSettings() {
        return SettingsResponse.from(getOrCreate());
    }

    public SettingsResponse updateSettings(UpdateSettingsRequest request) {
        Settings settings = getOrCreate();
        settings.setDownloadPath(request.downloadPath());
        return SettingsResponse.from(settingsRepository.save(settings));
    }

    public String getDownloadPath() {
        return getOrCreate().getDownloadPath();
    }

    private Settings getOrCreate() {
        return settingsRepository.findById(1L).orElseGet(() -> {
            Settings s = new Settings();
            s.setId(1L);
            s.setDownloadPath(defaultDownloadPath);
            return settingsRepository.save(s);
        });
    }
}
