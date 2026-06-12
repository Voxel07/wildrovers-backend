package helper;

import jakarta.enterprise.context.RequestScoped;
import model.User;

@RequestScoped
public class RequestUserCache {
    private User cachedUser;
    private boolean resolved = false;

    public User getCachedUser() {
        return cachedUser;
      }

    public void setCachedUser(User user) {
        this.cachedUser = user;
        this.resolved = true;
    }

    public boolean isResolved() {
        return resolved;
    }
}
