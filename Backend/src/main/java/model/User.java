package model;

import java.util.ArrayList;
import java.util.List;

import javax.json.bind.annotation.JsonbTransient;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

@Entity
@Table(name = "USER")
public class User {
    @Id
    @SequenceGenerator(name = "userSeq", sequenceName = "ZSEQ_USER_ID", allocationSize = 1, initialValue = 1)
    @GeneratedValue(generator = "userSeq")

    @Column(name = "id", unique = true)
    private Long id;

    @Column(name = "email", unique = true) 
    private String email;

    @Column(name = "userName", unique = true)
    private String username;
    
    @Column(name = "password")
    private String password;

    @Column(name = "firstName")
    private String firstName;
    
    @Column(name = "lastName")
    private String lastName;

    @Column(name ="role")
    private String role;

    @Column(name = "isActive")
    private boolean isActive;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "address_id", referencedColumnName = "id")
    private Address address;

    @OneToMany(mappedBy="usr",cascade = {CascadeType.ALL},fetch=FetchType.LAZY )
	private List<Phone> phones = new ArrayList<>();

     // @OneToMany(mappedBy="usr",cascade = {CascadeType.ALL},fetch=FetchType.LAZY )
    // private List<Posts> posts = new ArrayList<>();
    
    // @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.ALL})
    // @JoinTable(
    // 		name = "unreadPosts",
    // 		joinColumns = {@JoinColumn(name = "UserId", referencedColumnName="id")},
    // 		inverseJoinColumns = {@JoinColumn(name = "PostId", referencedColumnName="id")}
    // 		)
    // private  List<Posts> posts = new ArrayList<>();

    public User(){

    }
    
    public User(String email, String username, String password, String firstName, String lastName, String role, Boolean isActive) {
        this.email = email;
        this.username = username;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.role = role;
        this.isActive = isActive;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUserName() {
        return username;
    }

    public void setUserName(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    @JsonbTransient
    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    @JsonbTransient
    public List<Phone> getPhones(){
    	return this.phones;
    }
    public void setPhones(List<Phone> phones) {
    	this.phones = phones;
    	
    }
    public void removePhone(Phone phone) {
    	getPhones().remove(phone);
    }

    public boolean getActive() {
        return isActive;
    }

    public void setActive(boolean isActive) {
        this.isActive = isActive;
    }

  
   


}
