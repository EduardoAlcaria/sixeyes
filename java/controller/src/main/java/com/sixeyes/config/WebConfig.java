package com.sixeyes.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serves the built dashboard from this application, which is what lets nginx and the CORS
 * config go away: the SPA and the API are now the same origin, so the session cookie just
 * works and no cross-origin rules apply.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${api.prefix:/api/v1}")
    private String apiPrefix;

    /** Prefixes annotated controllers instead of using server.servlet.context-path, which
     *  would have pushed the static SPA under /api/v1 as well. */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(apiPrefix,
                HandlerTypePredicate.forBasePackage("com.sixeyes.controller"));
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String apiPath = apiPrefix.startsWith("/") ? apiPrefix.substring(1) : apiPrefix;

        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location)
                            throws IOException {
                        // An unmatched API route is a 404, not the SPA shell - returning
                        // HTML there would turn every API typo into a confusing 200.
                        if (resourcePath.startsWith(apiPath)) {
                            return null;
                        }
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // Client-side routes (/torrents, /settings) have no file on disk.
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }
}
