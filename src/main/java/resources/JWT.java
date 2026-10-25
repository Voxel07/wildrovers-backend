package resources;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import io.smallrye.jwt.algorithm.SignatureAlgorithm;
import io.smallrye.jwt.build.Jwt;
import model.User;

public class JWT {
    /** Claim carrying {@link User#getTokenVersion()} at issuance; checked on every request. */
    public static final String TOKEN_VERSION_CLAIM = "ver";

    /**
     * Generate a short-lived access token. The subject is the immutable user id;
     * username and email are never used to resolve the account behind a token.
     */
    public static String generator(User user, Duration ttl) {
      Instant now = Instant.now();
      return Jwt
            .issuer("wildrovers") //needs to match LocalJwtIdentityService
            .audience("wildrovers-backend")
            .subject(String.valueOf(user.getId()))
            .upn(user.getUserName())
            .claim(TOKEN_VERSION_CLAIM, user.getTokenVersion())
            .groups(Set.of(user.getRole()))
            .issuedAt(now)
            .expiresAt(now.plus(ttl))
            .jws()
            .algorithm(SignatureAlgorithm.RS256)
            .sign();
    }
}
