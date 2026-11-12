package com.jansetu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * JanSetu backend entry point.
 *
 * <p>JanSetu is an independent portfolio/simulation project. It is not an official government portal
 * and has no real government integrations.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class JanSetuApplication {

    public static void main(String[] args) {
        SpringApplication.run(JanSetuApplication.class, args);
    }
}
