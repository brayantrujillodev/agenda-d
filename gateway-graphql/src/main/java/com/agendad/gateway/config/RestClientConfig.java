package com.agendad.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    /**
     * Con el HttpClient del JDK: el HttpURLConnection por defecto rechaza
     * PATCH ("Invalid HTTP method"), y registrarAsistencia lo necesita para
     * PATCH /v1/citas/{id}/estado.
     */
    @Bean
    RestClient agendaRestClient(RestClient.Builder builder) {
        return builder.requestFactory(new JdkClientHttpRequestFactory()).build();
    }
}
