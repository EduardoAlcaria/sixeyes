package com.sixeyes.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Slf4j
@Configuration
public class RestTemplateConfig {

    /** Shared secret the Python agent requires. Applied as an interceptor rather than per
     *  call site so no future request to the agent can forget it. */
    public static final String AGENT_KEY_HEADER = "X-SixEyes-Agent-Key";

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder,
                                     @Value("${agent.key:}") String agentKey) {

        if (agentKey.isBlank()) {
            log.warn("agent.key is not set - every call to the Python agent will be "
                    + "rejected with 401. start.ps1 generates and exports AGENT_KEY.");
        }

        return authenticated(builder, agentKey, Duration.ofSeconds(10));
    }

    private static RestTemplate authenticated(RestTemplateBuilder builder, String agentKey,
                                              Duration readTimeout) {
        return builder
                // Plain HTTP/1.1: the JDK client's default h2c upgrade makes uvicorn log
                // "Unsupported upgrade request" on every poll.
                .requestFactory(SimpleClientHttpRequestFactory.class)
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(readTimeout)
                .additionalInterceptors((request, body, execution) -> {
                    request.getHeaders().set(AGENT_KEY_HEADER, agentKey);
                    return execution.execute(request, body);
                })
                .build();
    }
}
