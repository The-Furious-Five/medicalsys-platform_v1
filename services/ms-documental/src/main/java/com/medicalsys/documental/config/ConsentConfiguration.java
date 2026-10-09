package com.medicalsys.documental.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ConsentIntegrationProperties.class)
public class ConsentConfiguration {
}
