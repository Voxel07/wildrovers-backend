package model.Users;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import io.quarkus.security.jpa.UserDefinition;

@Entity
@Table(name = "KEYS")
@UserDefinition 
public class Keys {

    @Id
    @SequenceGenerator(name = "KeysSeq", sequenceName = "ZSEQ_KEYS_ID", allocationSize = 1, initialValue = 1)
    @GeneratedValue(generator = "KeysSeq")
    
    @Column
    String password;

    @Column
    Boolean isVerifyed;

    @Column 
    String verificationId;

    public Keys(){

    }

    public Keys(String password, Boolean isVerifyed, String verificationId) {
        this.password = password;
        this.isVerifyed = isVerifyed;
        this.verificationId = verificationId;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Boolean getIsVerifyed() {
        return isVerifyed;
    }

    public void setIsVerifyed(Boolean isVerifyed) {
        this.isVerifyed = isVerifyed;
    }

    public String getVerificationId() {
        return verificationId;
    }

    public void setVerificationId(String verificationId) {
        this.verificationId = verificationId;
    }

    

    
}
