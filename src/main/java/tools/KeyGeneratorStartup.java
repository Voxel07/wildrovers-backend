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
        File privFile = new File("src/main/resources/privateKey.pem");
        File pubFile = new File("src/main/resources/publicKey.pem");
        
        // Force regeneration to replace any invalid Web Crypto formatting
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
