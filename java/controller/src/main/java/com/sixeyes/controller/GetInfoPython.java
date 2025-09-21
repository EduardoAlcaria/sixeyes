package com.sixeyes.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestTemplate;

@Service
public class GetInfoPython {

    @Value("${python.service.url}")
    private String pythonUrl;
    private final RestTemplate restTemplate = new RestTemplate();

    public String getStorage(){
        String flaskURL = pythonUrl + "/python/systemInfo/getStorageInfo";
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(flaskURL, String.class);
            return response.getBody();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
