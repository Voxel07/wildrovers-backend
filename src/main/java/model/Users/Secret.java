package model.Users;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;



import model.User;

@Entity
@Table(name = "SECRET")
public class Secret {

    @Id
    @SequenceGenerator(name = "KeysSeq", sequenceName = "ZSEQ_KEYS_ID", allocationSize = 1, initialValue = 1)
    @GeneratedValue(generator = "KeysSeq")
    
    @Column(name = "id", unique = true)
    private Long id;

    // @Column(name = "password")
    // String password;

    @Column(name = "isVerifyed")
    Boolean isVerifyed;

    @Column(name = "verificationId") 
    String verificationId;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;


    public Secret(){

    }

    public Secret(/*String password,*/ Boolean isVerifyed, String verificationId) {
        // this.password = password;
        this.isVerifyed = isVerifyed;
        this.verificationId = verificationId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    // public String getPassword() {
    //     return password;
    // }

    // public void setPassword(String password) {
    //     this.password = password;
    // }

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
