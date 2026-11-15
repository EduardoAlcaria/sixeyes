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

    @Value("${settings.default-download-path:./downloads}")
    private String defaultDownloadPath;

    @Value("${install.root:}")
    private String defaultInstallRoot;

    public SettingsResponse getSettings() {
        Settings s = getOrCreate();
        return new SettingsResponse(s.getDownloadPath(), getInstallPath());
    }

    public SettingsResponse updateSettings(UpdateSettingsRequest request) {
        Settings settings = getOrCreate();
        settings.setDownloadPath(request.downloadPath().trim());
        settings.setInstallPath(request.installPath() == null || request.installPath().isBlank()
                ? null : request.installPath().trim());
        settingsRepository.save(settings);
        return getSettings();
    }

    public String getDownloadPath() {
        return getOrCreate().getDownloadPath();
    }

    /** Settings value, else the INSTALL_ROOT env default; null when neither is set. */
    public String getInstallPath() {
        String saved = getOrCreate().getInstallPath();
        if (saved != null) {
            return saved;
        }
        return defaultInstallRoot.isBlank() ? null : defaultInstallRoot;
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
