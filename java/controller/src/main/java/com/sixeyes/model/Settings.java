package com.sixeyes.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Table(name = "settings")
@NoArgsConstructor
public class Settings {

    @Id
    private Long id;

    @Column(name = "download_path", nullable = false)
    private String downloadPath;

    /** Default folder games are installed into; null falls back to install.root. */
    @Column(name = "install_path", length = 4000)
    private String installPath;

    /** The Command Deck layout (order, hidden widgets, saved presets) as JSON, shared by every browser. */
    @Column(name = "layout", length = 100000)
    private String layout;
}
