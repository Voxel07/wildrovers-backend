package resources;

import java.util.Arrays;
import java.util.HashSet;

import io.smallrye.jwt.algorithm.SignatureAlgorithm;
import io.smallrye.jwt.build.Jwt;
import model.User;

import jakarta.json.JsonObject;
import jakarta.json.Json;
import java.time.Duration;
import java.time.Instant;

public class JWT {
    /**
     * Generate JWT token
     */
    public static String generator(User user) {
      return Jwt
            .issuer("wildrovers") //needs to match application properties
            .audience("wildrovers-backend")
            .upn(user.getUserName())
            .claim("email", user.getEmail())
            .groups(new HashSet<>(Arrays.asList(user.getRole())))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plus(Duration.ofHours(12)))
            .jws()
            .algorithm(SignatureAlgorithm.RS256)
            .sign();
    }


    public static JsonObject createReactAuthObject(String token, User user){
      return Json.createObjectBuilder()
      .add("JWT", token)
      .add("USER", Json.createObjectBuilder()
              .add("Name", user.getUserName())
              .add("Role", user.getRole())
              .add("canCreateCategory", user.getCanCreateCategory()).build()
              )
      .build();
    }

}
