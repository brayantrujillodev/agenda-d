package com.agendad.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    RestClient agendaRestClient(RestClient.Builder builder) {
        return builder.build();
    }
}