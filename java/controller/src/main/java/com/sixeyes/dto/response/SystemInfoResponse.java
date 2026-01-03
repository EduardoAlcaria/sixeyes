package com.sixeyes.dto.response;

public record SystemInfoResponse(StorageInfo storage, NetworkInfo network) {

    public record StorageInfo(double total, double used, double available, String device) {}
    public record NetworkInfo(double downloadSpeed, double uploadSpeed) {}
}
