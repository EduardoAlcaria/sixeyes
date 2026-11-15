package com.sixeyes.repo;

import com.sixeyes.model.TrustedDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrustedDeviceRepository extends JpaRepository<TrustedDevice, Long> {

    Optional<TrustedDevice> findByDeviceHash(String deviceHash);

    List<TrustedDevice> findAllByOrderByCreatedAtDesc();

    long countByStatus(TrustedDevice.Status status);
}
