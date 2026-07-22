package request.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import java.net.http.HttpClient;
import java.time.Duration;

@Getter
@Configuration
public class HttpClientConfig {
    @Value("${github.base-url}")
    private String baseUrl;

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }
}
