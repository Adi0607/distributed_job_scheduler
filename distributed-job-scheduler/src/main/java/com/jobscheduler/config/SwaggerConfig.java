package com.jobscheduler.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger configuration for auto-generated API documentation.
 */
@Configuration
public class SwaggerConfig {

    /**
     * Configures the OpenAPI specification with project metadata and server info.
     *
     * @return configured OpenAPI instance
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Distributed Job Scheduler API")
                        .version("1.0.0")
                        .description("Production-grade distributed job scheduler with Redis locking, " +
                                "priority queues, exponential retry backoff, and pluggable handlers.")
                        .contact(new Contact()
                                .name("Backend SDE Portfolio")
                                .email("dev@jobscheduler.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Development"),
                        new Server().url("http://app:8080").description("Docker Compose")
                ));
    }
}
