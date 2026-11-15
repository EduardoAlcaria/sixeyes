package com.sixeyes.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IgdbTitleTest {

    private final IgdbService igdb = new IgdbService("", "", null);

    @Test
    void stripsFitGirlVersionAndDlcSuffixes() {
        assertThat(igdb.stripSuffixes("Hollow Knight – v1.5.78 + 4 DLCs")).isEqualTo("Hollow Knight");
        assertThat(igdb.stripSuffixes("Celeste, v1.4.0.0")).isEqualTo("Celeste");
        assertThat(igdb.stripSuffixes("Hades")).isEqualTo("Hades");
    }
}
