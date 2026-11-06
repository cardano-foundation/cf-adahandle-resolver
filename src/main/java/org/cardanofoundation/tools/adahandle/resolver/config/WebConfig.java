package org.cardanofoundation.tools.adahandle.resolver.config;

import lombok.RequiredArgsConstructor;
import org.cardanofoundation.tools.adahandle.resolver.sync.SyncStatusHeaders;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableWebMvc
@EnableScheduling
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final SyncStatusHeaders syncStatusHeaders;

    @Override
    public void addCorsMappings(CorsRegistry registry) {

        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH")
                // Without this, a browser hides the sync status headers from the page's scripts.
                .exposedHeaders(SyncStatusHeaders.ALL.toArray(String[]::new))
                .allowCredentials(true).maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(syncStatusHeaders).addPathPatterns("/api/**");
    }
}
