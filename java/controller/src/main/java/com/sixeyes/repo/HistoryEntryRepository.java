package com.sixeyes.repo;

import com.sixeyes.model.HistoryEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HistoryEntryRepository extends JpaRepository<HistoryEntry, Long> {

    Optional<HistoryEntry> findByTorrentId(Long torrentId);

    List<HistoryEntry> findAllByOrderByAddedAtDesc();
}
