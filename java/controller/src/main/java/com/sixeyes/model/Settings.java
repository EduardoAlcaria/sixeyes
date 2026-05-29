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
}
