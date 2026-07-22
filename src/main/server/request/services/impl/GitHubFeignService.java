package request.services.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import request.constants.Messages;
import request.dto.github.GitHubUser;
import request.services.interfaces.GitHubFeignClient;
import request.services.interfaces.IGitHubService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@Profile("prod")
public class GitHubFeignService implements IGitHubService {
    @Autowired
    private GitHubFeignClient feignClient;

    @Override
    public List<GitHubUser> getAllUsers() {
        List<GitHubUser> users = feignClient.getAllUsers();
        log.info(Messages.FEIGN_CLIENT_DATA, users);
        return users;
    }

    @Override
    public GitHubUser getUserByUsername(String username) {
        GitHubUser user = feignClient.getUserByUsername(username);
        log.info(Messages.FEIGN_CLIENT_DATA, user);
        return user;
    }
}
