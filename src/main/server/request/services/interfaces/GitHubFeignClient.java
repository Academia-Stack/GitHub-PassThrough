package request.services.interfaces;

import request.config.FeignClientConfig;
import request.dto.github.GitHubUser;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "github", url = "${github.base-url}", configuration = FeignClientConfig.class)
public interface GitHubFeignClient {
    @GetMapping("/users")
    List<GitHubUser> getAllUsers();

    @GetMapping("/users/{username}")
    GitHubUser getUserByUsername(@PathVariable("username") String username);
}
