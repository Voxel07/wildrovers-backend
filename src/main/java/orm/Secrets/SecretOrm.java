package orm.Secrets;

import java.util.UUID;
import model.Users.Secret;
import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SecretOrm {
    
    public String generateVerificationId(){

        return UUID.randomUUID().toString();
    }
}
