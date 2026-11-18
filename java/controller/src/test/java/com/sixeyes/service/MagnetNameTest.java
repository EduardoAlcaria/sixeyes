package com.sixeyes.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MagnetNameTest {

    @Test
    void readsTheDisplayNameFromTheMagnet() {
        assertThat(TorrentService.magnetName("magnet:?xt=urn:btih:abc&dn=Mass+Effect%3A+Andromeda&tr=udp://x"))
                .isEqualTo("Mass Effect: Andromeda");
    }

    @Test
    void noNameWhenAbsentOrMalformed() {
        assertThat(TorrentService.magnetName("magnet:?xt=urn:btih:abc")).isNull();
        assertThat(TorrentService.magnetName("magnet:?xt=urn:btih:abc&dn=%zz")).isNull();
    }
}
