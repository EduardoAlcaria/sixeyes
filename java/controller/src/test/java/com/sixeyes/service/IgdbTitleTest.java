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

    @Test
    void retriesWithoutTheEditionAndSubtitle() {
        assertThat(igdb.searchTitles("Mass Effect 3: Legendary Edition"))
                .containsExactly("Mass Effect 3: Legendary Edition", "Mass Effect 3");
        assertThat(igdb.searchTitles("Mass Effect 3: Legendary Edition")).hasSize(2);
        assertThat(igdb.searchTitles("Hades")).containsExactly("Hades");
        assertThat(igdb.searchTitles("Dishonored Definitive Edition"))
                .containsExactly("Dishonored Definitive Edition", "Dishonored");
        assertThat(igdb.searchTitles("Baldur's Gate: Enhanced Edition"))
                .containsExactly("Baldur's Gate: Enhanced Edition", "Baldur's Gate");
    }
}
