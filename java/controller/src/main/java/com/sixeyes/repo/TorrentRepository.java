package com.sixeyes.repo;

import com.sixeyes.model.Torrent;
import com.sixeyes.model.TorrentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface TorrentRepository extends JpaRepository<Torrent, Long> {

    boolean existsByMagnet(String magnet);

    List<Torrent> findByStatus(TorrentStatus status);

    List<Torrent> findByStatusIn(Collection<TorrentStatus> statuses);
}
