package model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.json.bind.annotation.JsonbTransient;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;

import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Id;

import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.GeneratedValue;

import io.quarkus.security.jpa.Password;
import io.quarkus.security.jpa.Roles;
import io.quarkus.security.jpa.UserDefinition;
import io.quarkus.security.jpa.Username;
import io.quarkus.elytron.security.common.BcryptUtil;
import model.Forum.ForumAnswer;
import model.Forum.ForumCategory;
import model.Forum.ForumPost;
import model.Forum.ForumTopic;
import model.Users.ActivityForum;
import model.Users.Secret;

@Entity
@Table(name = "USER")
@UserDefinition 
public class User {
    @Id
    @SequenceGenerator(name = "userSeq", sequenceName = "ZSEQ_USER_ID", allocationSize = 1, initialValue = 1)
    @GeneratedValue(generator = "userSeq")

    @Column(name = "id", unique = true)
    private Long id;

    @Column(name = "email", unique = true) 
    private String email;

    @Username
    @Column(name = "userName", unique = true)
    private String userName;

    @Password 
    @Column(name = "password")
    private String password;

    @Column(name = "firstName")
    private String firstName;
    
    @Column(name = "lastName")
    private String lastName;

    @Roles 
    @Column(name ="role")
    private String role;

    @Column(name = "isActive")
    private boolean isActive;

    @Column(name = "lastLogin")
    private LocalDate lastLogin;

    @Column(name = "regestrationDate")
    private LocalDate regDate;

    @OneToOne(cascade = CascadeType.ALL, mappedBy = "userAddress")
    private Address address;

    @OneToOne(cascade = CascadeType.ALL, mappedBy = "user")
    private ActivityForum activityForum;

    @OneToOne(cascade = {CascadeType.ALL}, mappedBy = "user")
    private Secret keys;

    @OneToMany(mappedBy="user",cascade = {CascadeType.ALL},fetch=FetchType.LAZY )
	private List<Phone> phones = new ArrayList<>();

    @OneToMany(mappedBy = "creator",cascade = {CascadeType.ALL},fetch = FetchType.LAZY)
    private List<ForumCategory> categories = new ArrayList<>();
    
    @OneToMany(mappedBy = "creator",cascade = {CascadeType.ALL},fetch = FetchType.LAZY)
    private List<ForumTopic> topics = new ArrayList<>();
    
    @OneToMany(mappedBy="creator",cascade = {CascadeType.ALL},fetch=FetchType.LAZY )
    private List<ForumPost> posts = new ArrayList<>();

    @OneToMany(mappedBy="editor",cascade = {CascadeType.ALL},fetch=FetchType.LAZY )
    private List<ForumPost> editedPosts = new ArrayList<>();

    @OneToMany(mappedBy="creator",cascade = {CascadeType.ALL},fetch=FetchType.LAZY )
    private List<ForumAnswer> answers = new ArrayList<>();

    @OneToMany(mappedBy="editor",cascade = {CascadeType.ALL},fetch=FetchType.LAZY )
    private List<ForumPost> editedAnswers = new ArrayList<>();

  
  
    public User(){

    }

    public void setAnswers(List<ForumAnswer> answers) {
        this.answers = answers;
    }

    public User(String email, String userName, String password, String firstName, String lastName, String role, Boolean isActive) {
        this.email = email;
        this.userName = userName;
        this.password = BcryptUtil.bcryptHash(password);
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
        return userName;
    }

    public void setUserName(String username) {
        this.userName = username;
    }
    @JsonIgnore
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

    public void setActive(boolean isActive) {
        this.isActive = isActive;
    }

    public LocalDate getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(LocalDate lastLogin) {
        this.lastLogin = lastLogin;
    }

    public LocalDate getRegDate() {
        return regDate;
    }

    public void setRegDate(LocalDate regDate) {
        this.regDate = regDate;
    }

    public boolean isActive() {
        return isActive;
    }
    @JsonbTransient
    public List<ForumCategory> getCategories() {
        return categories;
    }

    public void setCategories(List<ForumCategory> categories) {
        this.categories = categories;
    }
    @JsonbTransient
    public List<ForumTopic> getTopics() {
        return topics;
    }

    public void setTopics(List<ForumTopic> topics) {
        this.topics = topics;
    }
    @JsonbTransient
    public List<ForumPost> getEditedPosts() {
        return editedPosts;
    }

    public void setEditedPosts(List<ForumPost> editedPosts) {
        this.editedPosts = editedPosts;
    }
    @JsonbTransient
    public List<ForumPost> getEditedAnswers() {
        return editedAnswers;
    }

    public void setEditedAnswers(List<ForumPost> editedAnswers) {
        this.editedAnswers = editedAnswers;
    }
    @JsonbTransient
    public ActivityForum getActivityForum() {
        return activityForum;
    }

    public void setActivityForum(ActivityForum activityForum) {
        this.activityForum = activityForum;
    }
    @JsonbTransient
    public List<ForumPost> getPosts() {
        return posts;
    }

    public void setPosts(List<ForumPost> posts) {
        this.posts = posts;
    }
    @JsonbTransient
    public List<ForumAnswer> getAnswers() {
        return answers;
    }

    public Secret getKeys() {
        return keys;
    }

    public void setKeys(Secret keys) {
        this.keys = keys;
    }

    @Override
    public String toString() {
        return "User [activityForum=" + activityForum + ", address=" + address + ", answers=" + answers
                + ", categories=" + categories + ", editedAnswers=" + editedAnswers + ", editedPosts=" + editedPosts
                + ", email=" + email + ", firstName=" + firstName + ", id=" + id + ", isActive=" + isActive
                + ", lastLogin=" + lastLogin + ", lastName=" + lastName + ", password=" + password + ", phones="
                + phones + ", posts=" + posts + ", regDate=" + regDate + ", role=" + role + ", topics=" + topics
                + ", userName=" + userName + "]";
    }
}
