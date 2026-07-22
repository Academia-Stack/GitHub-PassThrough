package request.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class RestClientConfig {
    @Value("${github.base-url}")
    public String baseUrl;

    @Bean
    public RestClient githubRestClient(RestClient.Builder builder) {
        return builder.baseUrl(baseUrl).build();
    }
}
