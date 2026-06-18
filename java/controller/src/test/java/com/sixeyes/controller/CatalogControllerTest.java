package com.sixeyes.controller;

import com.sixeyes.dto.response.CatalogGameResponse;
import com.sixeyes.service.CatalogService;
import com.sixeyes.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CatalogController.class)
class CatalogControllerTest {

    @Autowired MockMvc mvc;
    @MockBean CatalogService service;
    @MockBean JwtService jwtService;

    @Test
    @WithMockUser
    void search_returns200WithContent() throws Exception {
        var game = new CatalogGameResponse(1L, "Cyberpunk 2077",
                "https://fitgirl-repacks.site/cyberpunk-2077/",
                "https://img.example.com/cover.jpg", "magnet:?xt=urn:btih:ABC", "42 GB",
                null, null, null, null, null, null, null);
        when(service.search(eq("cyber"), eq(0), eq(24)))
                .thenReturn(new PageImpl<>(List.of(game)));

        mvc.perform(get("/catalog/search").param("q", "cyber"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Cyberpunk 2077"))
                .andExpect(jsonPath("$.content[0].magnet").value("magnet:?xt=urn:btih:ABC"));
    }

    @Test
    @WithMockUser
    void search_returns400WhenQMissing() throws Exception {
        mvc.perform(get("/catalog/search"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void game_returns200WithDetail() throws Exception {
        var game = new CatalogGameResponse(1L, "Elden Ring",
                "https://fitgirl-repacks.site/elden-ring/",
                "https://img.example.com/cover.jpg", "magnet:?xt=urn:btih:DEF", "30 GB",
                null, null, null, null, null, null, null);
        when(service.getDetails("https://fitgirl-repacks.site/elden-ring/")).thenReturn(game);

        mvc.perform(get("/catalog/game").param("url", "https://fitgirl-repacks.site/elden-ring/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.magnet").value("magnet:?xt=urn:btih:DEF"));
    }

    @Test
    @WithMockUser
    void refresh_returns202() throws Exception {
        mvc.perform(post("/catalog/refresh").with(csrf()))
                .andExpect(status().isAccepted());
    }

    @Test
    void search_returns401WithoutAuth() throws Exception {
        mvc.perform(get("/catalog/search").param("q", "test"))
                .andExpect(status().isUnauthorized());
    }
}
