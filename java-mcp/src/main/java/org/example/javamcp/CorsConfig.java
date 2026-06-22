package org.example.javamcp;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**") // Applies to all endpoints including /mcp/sse
                        .allowedOrigins("*") // Allows MCP Inspector origin
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // Explicitly allows OPTIONS
                        .allowedHeaders("*") // Allows custom headers used by MCP
                        .exposedHeaders("Mcp-Session-Id");
            }
        };
    }
}
