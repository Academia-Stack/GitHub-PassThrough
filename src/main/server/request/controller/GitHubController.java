package request.controller;

import request.dto.github.GitHubUser;
import request.dto.github.GitHubUserList;
import request.services.interfaces.IGitHubService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/github")
public class GitHubController {
    @Autowired
    private IGitHubService githubUserService;

    @GetMapping(path = "/users", produces = { MediaType.APPLICATION_JSON_VALUE })
    public List<GitHubUser> getAllUsersJSON() {
        return githubUserService.getAllUsers();
    }

    @GetMapping(path = "/users", produces = { MediaType.APPLICATION_XML_VALUE })
    public GitHubUserList getAllUsersXML() {
        return GitHubUserList.builder()
                .users(githubUserService.getAllUsers())
                .build();
    }

    @GetMapping(path = "/users/{username}", produces = {
            MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE
    })
    public GitHubUser getUserByUsername(@PathVariable String username) {
        return githubUserService.getUserByUsername(username);
    }
}
