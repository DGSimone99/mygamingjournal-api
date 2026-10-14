package it.mygamingjournal.catalog;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class RawgClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    @Bean
    RawgApi rawgApi(RestClient.Builder builder, RawgProperties properties) {
        var requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return createRawgApi(builder.requestFactory(requestFactory), properties);
    }

    /** Public so tests can bind a MockRestServiceServer to the builder first. */
    public static RawgApi createRawgApi(RestClient.Builder builder, RawgProperties properties) {
        RestClient client = builder
                .baseUrl(properties.baseUrl())
                .requestInterceptor(apiKeyInterceptor(properties.apiKey()))
                .build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(client)).build().createClient(RawgApi.class);
    }

    private static ClientHttpRequestInterceptor apiKeyInterceptor(String apiKey) {
        return (request, body, execution) -> {
            URI uri = UriComponentsBuilder.fromUri(request.getURI()).queryParam("key", apiKey).build(true).toUri();
            return execution.execute(new HttpRequestWrapper(request) {
                @Override
                public URI getURI() {
                    return uri;
                }
            }, body);
        };
    }
}
