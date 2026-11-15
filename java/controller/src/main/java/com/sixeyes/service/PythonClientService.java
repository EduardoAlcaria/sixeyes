package com.sixeyes.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sixeyes.dto.response.DiskInfo;
import com.sixeyes.exception.InvalidMagnetException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class PythonClientService {

    private final RestTemplate restTemplate;

    public PythonClientService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Value("${python.service.url}")
    private String pythonUrl;

    private record StartDownloadBody(long id, String magnet,
                                     @JsonProperty("downloadPath") String downloadPath,
                                     @JsonProperty("skipParts") List<String> skipParts
    ) {}

    private record TorrentIdBody(long id, String magnet) {}
    private record RemoveBody(long id, String magnet, boolean deleteFiles) {}

    private record MkdirBody(String parent, String name) {}

    public void startDownload(Long id, String magnet, String downloadPath) {
        startDownload(id, magnet, downloadPath, List.of());
    }

    public void startDownload(Long id, String magnet, String downloadPath,
                              List<String> skipParts) {
        exchange("/python/add", HttpMethod.POST,
                new StartDownloadBody(id, magnet, downloadPath,
                        skipParts == null ? List.of() : skipParts));
    }

    public String magnetFromFile(byte[] torrentBytes, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ByteArrayResource resource = new ByteArrayResource(torrentBytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);

        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(
                    url("/python/parseMagnet"), entity, Map.class);

            if (resp == null || resp.get("magnet") == null) {
                throw new InvalidMagnetException("(file) " + filename);
            }

            return resp.get("magnet").toString();

        } catch (RestClientException e) {
            throw engineException("POST /python/parseMagnet", e);
        }
    }

    public List<Map<String, Object>> fetchAllTorrentData() {
        try {

            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url("/python/get"), HttpMethod.GET, jsonEntity(null),
                    new ParameterizedTypeReference<>() {}
            );

            List<Map<String, Object>> body = response.getBody();

            if (body == null){
                return Collections.emptyList();
            }

            return body;

        } catch (RestClientException e) {
            throw engineException("GET /python/get", e);
        }
    }

    public void pause(Long id, String magnet) {
        exchange("/python/pause", HttpMethod.PUT, new TorrentIdBody(id, magnet));
    }

    public void stop(Long id, String magnet) {
        exchange("/python/stop", HttpMethod.PUT, new TorrentIdBody(id, magnet));
    }

    public void resume(Long id, String magnet) {
        exchange("/python/resume", HttpMethod.PUT, new TorrentIdBody(id, magnet));
    }

    public void remove(Long id, String magnet, boolean deleteFiles) {
        exchange("/python/remove", HttpMethod.DELETE, new RemoveBody(id, magnet, deleteFiles));
    }

    public Map<String, Object> fetchStorageInfo() {
        try {

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url("/python/systemInfo/getStorageInfo"), HttpMethod.GET, jsonEntity(null),
                    new ParameterizedTypeReference<>() {}
            );

            Map<String, Object> body = response.getBody();

            if (body == null){
                return Map.of("Used", 0L, "Available", 0L);
            }

            return body;

        } catch (RestClientException e) {
            throw engineException("GET /python/systemInfo/getStorageInfo", e);
        }
    }

    public List<DiskInfo> fetchDisks() {
        try {

            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url("/python/systemInfo/getDisks"), HttpMethod.GET, jsonEntity(null),
                    new ParameterizedTypeReference<>() {}
            );

            List<Map<String, Object>> body = response.getBody();

            if (body == null){
                return Collections.emptyList();
            }

            return body.stream()
                    .map(d -> new DiskInfo(
                        str(d.get("path")),
                        str(d.get("device")),
                        toDouble(d.get("total")),
                        toDouble(d.get("used")),
                        toDouble(d.get("available"))
            )).toList();

        } catch (RestClientException e) {
            throw engineException("GET /python/systemInfo/getDisks", e);
        }
    }

    public Map<String, Object> browse(String path) {
        // Build the URI so the path query param is percent-encoded exactly once
        // (a pre-encoded String template would be double-encoded by RestTemplate).
        UriComponentsBuilder b = UriComponentsBuilder.fromUriString(url("/python/systemInfo/browse"));
        if (path != null && !path.isBlank()) {
            b.queryParam("path", path);
        }
        URI uri = b.build().encode().toUri();
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    uri, HttpMethod.GET, jsonEntity(null),
                    new ParameterizedTypeReference<>() {}
            );
            Map<String, Object> body = response.getBody();
            return body != null ? body : Map.of("path", "", "entries", List.of());
        } catch (RestClientException e) {
            throw engineException("GET /python/systemInfo/browse", e);
        }
    }

    public Map<String, Object> makeDir(String parent, String name) {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url("/python/systemInfo/mkdir"), HttpMethod.POST,
                    jsonEntity(new MkdirBody(parent, name)),
                    new ParameterizedTypeReference<>() {}
            );
            Map<String, Object> body = response.getBody();
            return body != null ? body : Map.of();
        } catch (RestClientException e) {
            throw engineException("POST /python/systemInfo/mkdir", e);
        }
    }

    /** GET returning the agent's JSON as-is (Map or List) - for read-only passthroughs. */
    public Object getJson(String path, Map<String, ?> query) {
        UriComponentsBuilder b = UriComponentsBuilder.fromUriString(url(path));
        if (query != null) {
            query.forEach((k, v) -> {
                if (v != null) b.queryParam(k, v);
            });
        }
        try {
            return restTemplate.exchange(b.build().encode().toUri(), HttpMethod.GET,
                    jsonEntity(null), Object.class).getBody();
        } catch (RestClientException e) {
            throw engineException("GET " + path, e);
        }
    }

    public Object postJson(String path, Object body) {
        try {
            return restTemplate.exchange(url(path), HttpMethod.POST, jsonEntity(body),
                    Object.class).getBody();
        } catch (RestClientException e) {
            throw engineException("POST " + path, e);
        }
    }

    private <T> void exchange(String path, HttpMethod method, T body) {
        try {

            restTemplate.exchange(url(path), method, jsonEntity(body), Void.class);

        } catch (RestClientException e) {
            throw engineException(method + " " + path, e);
        }
    }

    private String url(String path) {
        return pythonUrl + path;
    }

    private <T> HttpEntity<T> jsonEntity(T body) {

        HttpHeaders headers = new HttpHeaders();

        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        return new HttpEntity<>(body, headers);
    }

    private RuntimeException engineException(String operation, RestClientException cause) {

        // The agent rejecting a request (bad path, unreadable torrent) is the caller's
        // fault and should surface as a 400 with its reason, not a generic 500.
        if (cause instanceof HttpClientErrorException clientError) {
            Map<?, ?> body = clientError.getResponseBodyAs(Map.class);
            Object detail = body == null ? null : body.get("detail");
            log.warn("Python engine rejected [{}]: {}", operation, detail);
            return new IllegalArgumentException(
                    detail != null ? detail.toString() : "Engine rejected " + operation);
        }

        log.error("Python engine call failed [{}]: {}", operation, cause.getMessage());
        return new RuntimeException("Python engine unavailable during " + operation, cause);
    }

    private static String str(Object o) {
        if (o == null){
            return "";
        }

        return o.toString();
    }

    private static double toDouble(Object o) {
        if (o == null){
            return 0.0;
        }

        try {
            return Double.parseDouble(o.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
