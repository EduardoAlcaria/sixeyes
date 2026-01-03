package com.sixeyes.controller;

import com.sixeyes.dto.request.MkdirRequest;
import com.sixeyes.dto.response.DiskInfo;
import com.sixeyes.dto.response.SystemInfoResponse;
import com.sixeyes.service.SystemInfoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    @GetMapping("/browse")
    public ResponseEntity<Map<String, Object>> browse(@RequestParam(required = false) String path) {
        return ResponseEntity.ok(systemInfoService.browse(path));
    }

    @PostMapping("/mkdir")
    public ResponseEntity<Map<String, Object>> mkdir(@Valid @RequestBody MkdirRequest request) {
        return ResponseEntity.ok(systemInfoService.makeDir(request.parent(), request.name()));
    }
}
