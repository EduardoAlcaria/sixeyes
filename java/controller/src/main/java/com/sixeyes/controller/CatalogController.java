package com.sixeyes.controller;

import com.sixeyes.dto.response.CatalogGameResponse;
import com.sixeyes.service.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    @GetMapping("/search")
    public ResponseEntity<Page<CatalogGameResponse>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size
    ) {
        return ResponseEntity.ok(catalogService.search(q, page, size));
    }

    @GetMapping("/game")
    public ResponseEntity<CatalogGameResponse> game(@RequestParam String url) {
        return ResponseEntity.ok(catalogService.getDetails(url));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh() {
        catalogService.triggerRefresh();
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("status", "crawl started"));
    }
}
