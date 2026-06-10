import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.util.Base64;

/**
 * Utility to generate a JWKS (JSON Web Key Set) file from an RSA public key PEM file.
 * 
 * Usage: java JwksUtil.java [pemPath] [jwksPath]
 * Defaults: src/main/resources/publicKey.pem -> src/main/resources/publicKey.jwks
 * 
 * This is needed because Quarkus OIDC's 'service' application type with
 * discovery-enabled=false requires a JWKS file (jwks-path) for local JWT verification.
 */
public class JwksUtil {
    public static void main(String[] args) throws Exception {
        String pemPath = args.length > 0 ? args[0] : "src/main/resources/publicKey.pem";
        String jwksPath = args.length > 1 ? args[1] : "src/main/resources/publicKey.jwks";
        
        String pemContent = Files.readString(Path.of(pemPath));
        pemContent = pemContent.replace("-----BEGIN PUBLIC KEY-----", "")
                               .replace("-----END PUBLIC KEY-----", "")
                               .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(pemContent);
        
        X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        RSAPublicKey rsaKey = (RSAPublicKey) kf.generatePublic(spec);
        
        String n = base64Url(rsaKey.getModulus().toByteArray());
        String e = base64Url(rsaKey.getPublicExponent().toByteArray());
        
        String jwks = String.format("""
            {
              "keys": [
                {
                  "kty": "RSA",
                  "n": "%s",
                  "e": "%s",
                  "alg": "RS256",
                  "use": "sig",
                  "kid": "wildrovers-local"
                }
              ]
            }
            """, n, e);
        
        Files.writeString(Path.of(jwksPath), jwks);
        System.out.println("JWKS written to: " + jwksPath);
        System.out.println(jwks);
    }
    
    private static String base64Url(byte[] data) {
        // Strip leading zero byte if present (Java BigInteger quirk)
        if (data[0] == 0) {
            byte[] stripped = new byte[data.length - 1];
            System.arraycopy(data, 1, stripped, 0, stripped.length);
            data = stripped;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }
}
