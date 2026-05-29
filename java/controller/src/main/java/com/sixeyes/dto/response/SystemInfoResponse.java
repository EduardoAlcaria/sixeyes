package com.sixeyes.dto.response;

public record SystemInfoResponse(StorageInfo storage, NetworkInfo network) {

    public record StorageInfo(double total, double used, double available) {}
    public record NetworkInfo(double downloadSpeed, double uploadSpeed) {}
}
