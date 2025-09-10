package com.sixeyes.Services;

import com.sixeyes.model.Torrent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TorrentRepository extends JpaRepository<Torrent, Long> {
    Torrent findByStatus(Torrent torrent);
}
