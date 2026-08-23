package com.medicalsys.agenda.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(SchedulingProperties.class)
public class SchedulingConfiguration {

    @Bean
    public RestClient.Builder schedulingRestClientBuilder() {
        return RestClient.builder();
    }
}
