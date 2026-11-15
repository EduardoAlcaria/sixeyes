package com.sixeyes.service;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class TorrentServiceSpeedTest {

    @Test
    void formatSpeedIgnoresCommaDecimalDefaultLocale() {
        Locale original = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("pt-BR"));
        try {
            assertThat(TorrentService.formatSpeed("1.5")).isEqualTo("1.50 MB/s");
        } finally {
            Locale.setDefault(original);
        }
    }
}
