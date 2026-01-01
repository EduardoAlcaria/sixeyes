package com.sixeyes.service;

import com.sixeyes.dto.response.DiskInfo;
import com.sixeyes.dto.response.SystemInfoResponse;
import com.sixeyes.dto.response.SystemInfoResponse.NetworkInfo;
import com.sixeyes.dto.response.SystemInfoResponse.StorageInfo;
import com.sixeyes.model.TorrentStatus;
import com.sixeyes.repo.TorrentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SystemInfoService {

    private final PythonClientService pythonClient;
    private final TorrentRepository torrentRepository;

    private static final double GB = 1024.0 * 1024.0 * 1024.0;

    public SystemInfoResponse getSystemInfo() {
        Map<String, Object> raw = pythonClient.fetchStorageInfo();

        double usedBytes  = toDouble(raw.get("Used"));
        double freeBytes  = toDouble(raw.get("Available"));
        double totalBytes = raw.containsKey("Total")
                ? toDouble(raw.get("Total"))
                : usedBytes + freeBytes;

        StorageInfo storage = new StorageInfo(
                round(totalBytes / GB),
                round(usedBytes  / GB),
                round(freeBytes  / GB)
        );

        double totalDownload = torrentRepository.findByStatus(TorrentStatus.DOWNLOADING).stream()
                .mapToDouble(t -> parseSpeed(t.getDownloadSpeed()))
                .sum();

        double totalUpload = torrentRepository.findAll().stream()
                .mapToDouble(t -> parseSpeed(t.getUploadSpeed()))
                .sum();

        return new SystemInfoResponse(storage, new NetworkInfo(totalDownload, totalUpload));
    }

    public List<DiskInfo> getDiskList() {
        return pythonClient.fetchDisks();
    }

    private static double toDouble(Object value) {
        if (value == null){
            return 0.0;
        }

        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static double parseSpeed(String speed) {

        if (speed == null || speed.isBlank()){
            return 0.0;
        }

        try {
            return Double.parseDouble(speed.replace("MB/s", "").trim());
        } catch (NumberFormatException e) { return 0.0; }
    }
}
