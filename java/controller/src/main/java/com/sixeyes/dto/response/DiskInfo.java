package com.sixeyes.dto.response;

public record DiskInfo(String path, String device, double total, double used, double available) {}
