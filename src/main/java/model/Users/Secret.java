package model.Users;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;



import model.User;

@Entity
@Table(name = "SECRET")
public class Secret {

    @Id
    @SequenceGenerator(name = "KeysSeq", sequenceName = "ZSEQ_KEYS_ID", allocationSize = 1, initialValue = 1)
    @GeneratedValue(generator = "KeysSeq")
    
    @Column(name = "id", unique = true)
    private Long id;



    @Column(name = "isVerifyed")
    Boolean isVerifyed;

    @Column(name = "verificationId") 
    String verificationId;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;


    public Secret(){

    }

    

    public Secret(Boolean isVerifyed, String verificationId) {
        this.isVerifyed = isVerifyed;
        this.verificationId = verificationId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    

    
}
