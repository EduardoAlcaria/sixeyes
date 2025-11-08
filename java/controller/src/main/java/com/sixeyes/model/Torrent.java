package com.sixeyes.model;


import jakarta.persistence.*;


import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.Objects;


@Entity
@Table(name = "Torrent")

public class Torrent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, unique = true, length = 4000)
    private String magnet;

    @Column(nullable = false)
    private String infoHash;


    private String size = "0 GB";
    private Double progress = 0.0;
    private String downloadSpeed = "0 MB/s";
    private String uploadSpeed = "0 MB/s";
    private Integer peers = 0;
    private String eta;
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Torrent() {
    }

    @Override
    public String toString() {
        return "Torrent{" + "id=" + id + ", title='" + title + '\'' + ", magnet='" + magnet + '\'' + ", infoHash='" + infoHash + '\'' + ", progress=" + progress + ", downloadSpeed='" + downloadSpeed + '\'' + ", uploadSpeed='" + uploadSpeed + '\'' + ", peers=" + peers + ", eta='" + eta + '\'' + ", createdAt=" + createdAt + ", updatedAt=" + updatedAt + '}';
    }

    public Torrent(String title, String magnet, String infoHash) {
        this.title = title;
        this.magnet = magnet;
        this.infoHash = infoHash;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Torrent torrent = (Torrent) o;
        return Objects.equals(id, torrent.id) && Objects.equals(title, torrent.title) && Objects.equals(magnet, torrent.magnet) && Objects.equals(infoHash, torrent.infoHash) && Objects.equals(progress, torrent.progress) && Objects.equals(downloadSpeed, torrent.downloadSpeed) && Objects.equals(uploadSpeed, torrent.uploadSpeed) && Objects.equals(peers, torrent.peers) && Objects.equals(eta, torrent.eta) && Objects.equals(status, torrent.status) && Objects.equals(createdAt, torrent.createdAt) && Objects.equals(updatedAt, torrent.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, magnet, infoHash, progress, downloadSpeed, uploadSpeed, peers, eta, status, createdAt, updatedAt);
    }

    public String getSize() {
        return this.size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public Torrent(String magnet) {
        this.magnet = magnet;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMagnet() {
        return magnet;
    }

    public void setMagnet(String magnet) {
        this.magnet = magnet;
    }

    public String getInfoHash() {
        return infoHash;
    }

    public void setInfoHash(String infoHash) {
        this.infoHash = infoHash;
    }


    public Double getProgress() {
        return progress;
    }

    public void setProgress(Object progress) {
        DecimalFormat decimalFormat = new DecimalFormat("#.##");
        this.progress = Double.parseDouble(decimalFormat.format(progress));
    }

    public String getDownloadSpeed() {
        return downloadSpeed;
    }

    public void setDownloadSpeed(String downloadSpeed) {
        if (downloadSpeed == null || downloadSpeed.isEmpty()) {
            this.downloadSpeed = "0.00 MB/s";
            return;
        }
        try {

            String cleanSpeed = downloadSpeed.replace(" MB/s", "").replace("MB/s", "").trim();
            this.downloadSpeed = String.format("%.2f MB/s", Double.parseDouble(cleanSpeed));
        } catch (NumberFormatException e) {
            this.downloadSpeed = "0.00 MB/s";
        }
    }

    public String getUploadSpeed() {
        return uploadSpeed;
    }


    public void setUploadSpeed(String uploadSpeed) {
        if (uploadSpeed == null || uploadSpeed.isEmpty()) {
            this.uploadSpeed = "0.00 MB/s";
            return;
        }
        try {

            String cleanSpeed = uploadSpeed.replace(" MB/s", "").replace("MB/s", "").trim();
            this.uploadSpeed = String.format("%.2f MB/s", Double.parseDouble(cleanSpeed));
        } catch (NumberFormatException e) {
            this.uploadSpeed = "0.00 MB/s";
        }
    }


    public Integer getPeers() {
        return peers;
    }

    public void setPeers(Integer peers) {
        this.peers = peers;
    }

    public String getEta() {
        return eta;
    }

    public void setEta(String eta) {
        this.eta = eta;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
