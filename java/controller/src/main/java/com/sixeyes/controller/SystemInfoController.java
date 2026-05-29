package com.sixeyes.controller;

import com.sixeyes.dto.response.DiskInfo;
import com.sixeyes.dto.response.SystemInfoResponse;
import com.sixeyes.service.SystemInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/system")
@RequiredArgsConstructor
public class SystemInfoController {

    private final SystemInfoService systemInfoService;

    @GetMapping("/info")
    public ResponseEntity<SystemInfoResponse> getInfo() {
        return ResponseEntity.ok(systemInfoService.getSystemInfo());
    }

    @GetMapping("/disks")
    public ResponseEntity<List<DiskInfo>> getDisks() {
        return ResponseEntity.ok(systemInfoService.getDiskList());
    }
}
