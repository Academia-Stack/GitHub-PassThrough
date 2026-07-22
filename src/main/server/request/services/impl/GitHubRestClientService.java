package request.services.impl;

import lombok.extern.slf4j.Slf4j;
import request.aop.annotation.RateLimited;
import request.constants.Messages;
import request.dto.github.GitHubUser;
import request.services.interfaces.IGitHubService;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Service
@Profile("dev")
@ConditionalOnProperty(name = "github.client", havingValue = "restclient", matchIfMissing = true)
public class GitHubRestClientService implements IGitHubService {
    private final RestClient githubRestClient;

    public GitHubRestClientService(RestClient githubRestClient) {
        this.githubRestClient = githubRestClient;
    }

    @RateLimited
    public List<GitHubUser> getAllUsers() {
        List<GitHubUser> users = githubRestClient.get()
            .uri("/users")
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
        log.info(Messages.REST_CLIENT_DATA, users);
        return users;
    }

    @RateLimited
    public GitHubUser getUserByUsername(String username) {
        GitHubUser user = githubRestClient.get()
            .uri("/users/{username}", username)
            .retrieve()
            .body(GitHubUser.class);
        log.info(Messages.REST_CLIENT_DATA, user);
        return user;
    }
}
