package co.vinni.tienda.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Permite que los clientes Angular (otro puerto/dominio) llamen a la API REST. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] origenes;

    public WebConfig(@Value("${app.cors.origenes:http://localhost:4200,http://localhost:4201}") String[] origenes) {
        this.origenes = origenes;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOriginPatterns(origenes).allowedMethods("GET", "POST", "OPTIONS");
    }
}
