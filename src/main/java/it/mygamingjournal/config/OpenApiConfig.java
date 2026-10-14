package it.mygamingjournal.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Set;

@Configuration
public class OpenApiConfig {

    private static final Set<String> INPUT_SCHEMAS = Set.of("GameQuery", "PageParams");

    /**
     * Response DTOs always serialize every property (null included), so they are all "required" (present).
     * Nullability is not expressed here; the frontend marks nullable fields in src/api/types.ts.
     */
    @Bean
    OpenApiCustomizer responsePropertiesAlwaysPresent() {
        return openApi -> openApi.getComponents().getSchemas().forEach((name, schema) -> {
            if (!name.endsWith("Request") && !INPUT_SCHEMAS.contains(name) && schema.getProperties() != null) {
                schema.setRequired(new ArrayList<>(schema.getProperties().keySet()));
            }
        });
    }

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("MyGamingJournal API")
                        .version("2.0")
                        .description("User data, community features and a cached proxy to the RAWG video games "
                                + "database. Game data by RAWG (https://rawg.io)."))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
