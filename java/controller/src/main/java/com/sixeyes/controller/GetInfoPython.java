package com.sixeyes.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import org.springframework.web.client.RestTemplate;

@Service
public class GetInfoPython {

    @Value("${python.service.url}")
    private String pythonUrl;

    private final RestTemplate restTemplate;

    @Autowired
    public GetInfoPython(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String getStorage() {
        String flaskURL = pythonUrl + "/python/systemInfo/getStorageInfo";
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(flaskURL, String.class);
            return response.getBody();
        } catch (Exception e) {
            System.out.println("Failed to get storage from Flask: " + e.getMessage());
            throw new RuntimeException("Flask storage request failed", e);
        }
    }
}
