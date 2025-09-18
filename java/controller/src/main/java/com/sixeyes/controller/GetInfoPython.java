package com.sixeyes.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestTemplate;

@Service
public class GetInfoPython {

    private final String flaskURL = "http://127.0.0.1:9999/python/systemInfo/getStorageInfo";
    private final RestTemplate restTemplate = new RestTemplate();

    public String getStorage(){
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(flaskURL, String.class);
            return response.getBody();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
