package request.controller;

import request.dto.github.GitHubUser;
import request.dto.github.GitHubUserList;
import request.dto.server.ResponseDTO;
import request.services.interfaces.IGitHubService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    @GetMapping(path = "/users",
        produces = { MediaType.APPLICATION_JSON_VALUE })
    public ResponseEntity<ResponseDTO<List<GitHubUser>>> getAllUsersJSON() {
        return ResponseEntity.ok(
            ResponseDTO.<List<GitHubUser>>builder()
                .data(githubUserService.getAllUsers())
                .build());
    }

    @GetMapping(path = "/users",
        produces = { MediaType.APPLICATION_XML_VALUE })
    public ResponseEntity<ResponseDTO<GitHubUserList>> getAllUsersXML() {
        return ResponseEntity.ok(
            ResponseDTO.<GitHubUserList>builder()
                .data(GitHubUserList.builder()
                    .users(githubUserService.getAllUsers())
                    .build())
                .build());
    }

    @GetMapping(path = "/users/{username}", produces = {
        MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE
    })
    public ResponseEntity<ResponseDTO<GitHubUser>> getUserByUsername(@PathVariable String username) {
        return ResponseEntity.ok(
            ResponseDTO.<GitHubUser>builder()
                .data(githubUserService.getUserByUsername(username))
                .build());
    }
}
