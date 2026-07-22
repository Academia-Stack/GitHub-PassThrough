package request.services.interfaces;

import java.util.List;
import request.dto.github.GitHubUser;

public interface IGitHubService {
    public List<GitHubUser> getAllUsers();
    public GitHubUser getUserByUsername(String username);
}
