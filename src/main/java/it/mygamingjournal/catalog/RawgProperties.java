package it.mygamingjournal.catalog;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("rawg")
public record RawgProperties(String baseUrl, String apiKey) {

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
