package tools;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.io.File;
import java.io.FileWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

@ApplicationScoped
public class KeyGeneratorStartup {
    void onStart(@Observes StartupEvent ev) {
        String privateKeyPath = System.getenv().getOrDefault("JWT_PRIVATE_KEY_PATH", "src/main/resources/privateKey.pem");
        String publicKeyPath = System.getenv().getOrDefault("JWT_PUBLIC_KEY_PATH", "src/main/resources/publicKey.pem");
        
        File privFile = new File(privateKeyPath);
        File pubFile = new File(publicKeyPath);
        
        if (privFile.exists() && pubFile.exists()) {
            System.out.println(">>> RSA JWT Keys already exist. Skipping generation. <<<");
            return;
        }
        
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();
            
            Base64.Encoder encoder = Base64.getMimeEncoder(64, new byte[]{'\n'});
            
            String privatePem = "-----BEGIN PRIVATE KEY-----\n" + 
                                encoder.encodeToString(kp.getPrivate().getEncoded()) + 
                                "\n-----END PRIVATE KEY-----\n";
                                
            String publicPem = "-----BEGIN PUBLIC KEY-----\n" + 
                               encoder.encodeToString(kp.getPublic().getEncoded()) + 
                               "\n-----END PUBLIC KEY-----\n";
            
            privFile.getParentFile().mkdirs();
            
            try (FileWriter fw = new FileWriter(privFile)) {
                fw.write(privatePem);
            }
            try (FileWriter fw = new FileWriter(pubFile)) {
                fw.write(publicPem);
            }
            System.out.println(">>> RSA JWT Keys generated successfully on startup! <<<");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
