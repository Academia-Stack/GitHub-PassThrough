package request.services.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import request.aop.annotation.RateLimited;
import request.config.HttpClientConfig;
import request.constants.Messages;
import request.dto.github.GitHubUser;
import request.services.interfaces.IGitHubService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

@Slf4j
@Service
@Profile("dev")
@ConditionalOnProperty(name = "github.client", havingValue = "httpclient")
public class GitHubHttpClientService implements IGitHubService {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public GitHubHttpClientService(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            HttpClientConfig httpClientConfig) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUrl = httpClientConfig.getBaseUrl();
    }

    @Override
    @RateLimited
    public List<GitHubUser> getAllUsers() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/users"))
                .GET()
                .header("Accept", "application/json")
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            List<GitHubUser> users = objectMapper.readValue(response.body(), new TypeReference<>() {});
            log.info(Messages.HTTP_CLIENT_DATA, users);
            return users;
        } catch (Exception e) {
            log.error("Failed to fetch GitHub users", e);
            throw new RuntimeException("Failed to fetch GitHub users", e);
        }
    }

    @Override
    @RateLimited
    public GitHubUser getUserByUsername(String username) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/users/" + username))
                .GET()
                .header("Accept", "application/json")
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            GitHubUser user = objectMapper.readValue(response.body(), GitHubUser.class);
            log.info(Messages.HTTP_CLIENT_DATA, user);
            return user;
        } catch (Exception e) {
            log.error("Failed to fetch GitHub user: {}", username, e);
            throw new RuntimeException("Failed to fetch GitHub user: " + username, e);
        }
    }
}
