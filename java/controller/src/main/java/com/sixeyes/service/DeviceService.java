package com.sixeyes.service;

import com.sixeyes.model.TrustedDevice;
import com.sixeyes.repo.TrustedDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Device pairing: a correct password on an unrecognised browser is not enough to get in.
 *
 * <p>The very first device is trusted on sight, because otherwise there would be no
 * approved device from which to approve anything. Every device after that starts PENDING
 * and has to be approved from a device already trusted.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final TrustedDeviceRepository repo;
    private final EventService events;

    @Transactional
    public TrustedDevice resolve(String rawDeviceId, String userAgent) {
        String hash = Secrets.sha256(rawDeviceId);
        Instant now = Instant.now();

        return repo.findByDeviceHash(hash)
                .map(existing -> {
                    existing.setLastSeenAt(now);
                    return repo.save(existing);
                })
                .orElseGet(() -> {
                    boolean bootstrap = repo.countByStatus(TrustedDevice.Status.TRUSTED) == 0;

                    TrustedDevice device = new TrustedDevice();
                    device.setDeviceHash(hash);
                    device.setLabel(describe(userAgent));
                    device.setStatus(bootstrap
                            ? TrustedDevice.Status.TRUSTED
                            : TrustedDevice.Status.PENDING);
                    device.setCreatedAt(now);
                    device.setLastSeenAt(now);
                    if (bootstrap) {
                        device.setApprovedAt(now);
                        log.info("First device seen ({}) - trusted on first use", device.getLabel());
                    } else {
                        log.warn("New device awaiting approval: {}", device.getLabel());
                        events.record("DEVICE_PENDING", com.sixeyes.model.AppEvent.Level.WARN,
                                "New device waiting for approval: " + device.getLabel());
                    }
                    return repo.save(device);
                });
    }

    @Transactional
    public TrustedDevice approve(Long id) {
        TrustedDevice device = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown device " + id));
        device.setStatus(TrustedDevice.Status.TRUSTED);
        device.setApprovedAt(Instant.now());
        events.record("DEVICE_APPROVED", com.sixeyes.model.AppEvent.Level.OK,
                "Dispositivo aprovado: " + device.getLabel());
        return repo.save(device);
    }

    /** Returns the device hash so the caller can kill its sessions in the same breath. */
    @Transactional
    public String remove(Long id) {
        TrustedDevice device = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown device " + id));
        repo.delete(device);
        return device.getDeviceHash();
    }

    public List<TrustedDevice> list() {
        return repo.findAllByOrderByCreatedAtDesc();
    }

    /** Best-effort label from the User-Agent, only so approval is not a blind guess. */
    private static String describe(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown device";
        }
        String os = userAgent.contains("Windows") ? "Windows"
                : userAgent.contains("Android") ? "Android"
                : userAgent.contains("iPhone") || userAgent.contains("iPad") ? "iOS"
                : userAgent.contains("Mac OS") ? "macOS"
                : userAgent.contains("Linux") ? "Linux"
                : "Unknown OS";
        String browser = userAgent.contains("Edg/") ? "Edge"
                : userAgent.contains("OPR/") ? "Opera"
                : userAgent.contains("Firefox") ? "Firefox"
                : userAgent.contains("Chrome") ? "Chrome"
                : userAgent.contains("Safari") ? "Safari"
                : "Unknown browser";
        return browser + " on " + os;
    }
}
