package com.sixeyes.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.*;


@CrossOrigin(origins = "http://localhost:3000")
@RestController
public class ApiRestController {
    //refactor python to receive the magnet from the spring boot code and the requests


    @PostMapping("/api/torrents")
    public ResponseEntity<String> addTorrent(@RequestBody DTO dto) throws IOException, InterruptedException {

        System.out.println("magnet received " + dto.getMagnet());

        TorrentDownloader.downloader(dto.getMagnet());

        return new ResponseEntity<>("OK",HttpStatus.OK);
    }
}