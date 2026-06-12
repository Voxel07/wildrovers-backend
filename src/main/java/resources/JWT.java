package resources;

import java.util.Arrays;
import java.util.HashSet;

import io.smallrye.jwt.algorithm.SignatureAlgorithm;
import io.smallrye.jwt.build.Jwt;
import model.User;

import jakarta.json.JsonObject;
import jakarta.ws.rs.core.NewCookie;
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
            .upn(user.getUserName())
            .claim("email", user.getEmail())
            .groups(new HashSet<>(Arrays.asList(user.getRole())))
            .expiresAt(Instant.now().plus(Duration.ofDays(30)))
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

    public static NewCookie generateCookie(String token){
      return new NewCookie.Builder("__refresh_Token__")
          .value(token)
          .path("/")
          .domain("localhost")
          .comment("test")
          .maxAge(3600 * 24 * 30)
          .secure(true)
          .httpOnly(true)
          .build();
    }

    public static NewCookie removeCookie(){
      return new NewCookie.Builder("__refresh_Token__")
          .value("deleted")
          .path("/")
          .domain("localhost:3000")
          .comment("test")
          .maxAge(0)
          .secure(true)
          .httpOnly(true)
          .build();
    }

}