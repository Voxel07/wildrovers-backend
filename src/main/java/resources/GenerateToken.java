package resources;

import java.util.Arrays;
import java.util.HashSet;

import org.eclipse.microprofile.jwt.Claims;

import io.smallrye.jwt.algorithm.KeyEncryptionAlgorithm;
import io.smallrye.jwt.algorithm.SignatureAlgorithm;
import io.smallrye.jwt.build.Jwt;


import java.time.Duration;
import java.time.Instant;
public class GenerateToken {
    /**
     * Generate JWT token
     */
    public static String generator(String role , String name) {

      return Jwt
            .issuer("wildrovers") //needs to match application properties
            .upn(name)
            .groups(new HashSet<>(Arrays.asList(role)))
            .expiresAt(Instant.now().plus(Duration.ofDays(30)))
            .claim(Claims.birthdate.name(), "2001-07-13")
            .jws()
            .algorithm(SignatureAlgorithm.RS256)
            .sign();
    }
}