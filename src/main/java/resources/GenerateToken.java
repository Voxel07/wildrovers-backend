package resources;

import java.util.Arrays;
import java.util.HashSet;

import org.eclipse.microprofile.jwt.Claims;

import io.smallrye.jwt.build.Jwt;

public class GenerateToken {
    /**
     * Generate JWT token
     */
    public static String generator(String role , String name) {
      return Jwt.issuer("test")
             .upn(name)
             .groups(new HashSet<>(Arrays.asList(role)))
             .claim(Claims.birthdate.name(), "2001-07-13")
           .sign("C:\\Hompage\\getting-started\\src\\main\\resources\\META-INF\\resources\\privateKey.pem");

    }
}