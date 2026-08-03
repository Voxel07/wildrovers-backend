package helper;

import io.quarkus.security.identity.request.BaseAuthenticationRequest;

public final class LocalJwtAuthenticationRequest extends BaseAuthenticationRequest {
    private final String token;

    public LocalJwtAuthenticationRequest(String token) {
        this.token = token;
    }

    public String token() {
        return token;
    }
}
