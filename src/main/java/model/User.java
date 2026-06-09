package model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.json.bind.annotation.JsonbTransient;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;

import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;

import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.GeneratedValue;

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

//Validator
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;

@Entity
@Table(name = "USER")
@UserDefinition
public class User {
    @Id
    @SequenceGenerator(name = "userSeq", sequenceName = "ZSEQ_USER_ID", allocationSize = 1, initialValue = 1)
    @GeneratedValue(generator = "userSeq")

    @Column(name = "id", unique = true)
    private Long id;

    @NotBlank(message = "Die E-Mail muss gesetzt sein.")
    @Column(name = "email", unique = true)
    private String email;

    @NotBlank(message = "Der Benutzername muss gesetzt sein.")
    @Username
    @Column(name = "userName", unique = true)
    private String userName;

    @NotBlank(message = "Das Passwort muss gesetzt sein.")
    @Length(min = 8, max = 256)
    @Password
    @Column(name = "password")
    private String password;

    @NotBlank(message = "Der Vorname muss gesetzt sein.")
    @Column(name = "firstName")
    private String firstName;

    @NotBlank(message = "Der Nachname muss gesetzt sein.")
    @Column(name = "lastName")
    private String lastName;

    @Roles
    @Column(name = "role")
    private String role;

    @Column(name = "isActive")
    private boolean isActive;

    @Column(name = "lastLogin")
    private Long lastLogin;

    @Column(name = "regestrationDate")
    private Long regDate;

    @OneToOne(cascade = CascadeType.ALL, mappedBy = "userAddress")
    private Address address;

    @OneToOne(cascade = CascadeType.ALL, mappedBy = "user")
    private ActivityForum activityForum;

    @JsonIgnore
    @JsonbTransient
    @OneToOne(cascade = { CascadeType.ALL }, mappedBy = "user")
    private Secret secret;

    @Column(name = "photoUrl")
    private String photoUrl;

    @Column(name = "phrase")
    private String phrase;

    @Column(name = "birthday")
    private LocalDate birthday;

    @Column(name = "visitedOps")
    private Integer visitedOps;

    @Column(name = "ribbon")
    private String ribbon;

    @Column(name = "yearlyFeePaid", columnDefinition = "boolean default false")
    private Boolean yearlyFeePaid = false;

    @jakarta.persistence.Transient
    private Long eventsAttended = 0L;

    @JsonIgnore
    @JsonbTransient
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentor_id")
    private User mentor;

    @JsonIgnore
    @JsonbTransient
    @OneToMany(mappedBy = "mentor", fetch = FetchType.LAZY)
    private List<User> mentees = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = { CascadeType.ALL }, fetch = FetchType.LAZY)
    private List<Phone> phones = new ArrayList<>();

    @OneToMany(mappedBy = "creator", cascade = { CascadeType.ALL }, fetch = FetchType.LAZY)
    private List<ForumCategory> categories = new ArrayList<>();

    @OneToMany(mappedBy = "creator", cascade = { CascadeType.ALL }, fetch = FetchType.LAZY)
    private List<ForumTopic> topics = new ArrayList<>();

    @OneToMany(mappedBy = "creator", cascade = { CascadeType.ALL }, fetch = FetchType.LAZY)
    private List<ForumPost> posts = new ArrayList<>();

    @OneToMany(mappedBy = "editor", cascade = { CascadeType.ALL }, fetch = FetchType.LAZY)
    private List<ForumPost> editedPosts = new ArrayList<>();

    @OneToMany(mappedBy = "creator", cascade = { CascadeType.ALL }, fetch = FetchType.LAZY)
    private List<ForumAnswer> answers = new ArrayList<>();

    @OneToMany(mappedBy = "editor", cascade = { CascadeType.ALL }, fetch = FetchType.LAZY)
    private List<ForumPost> editedAnswers = new ArrayList<>();

    public User() {

    }

    public void setAnswers(List<ForumAnswer> answers) {
        this.answers = answers;
    }

    public User(String email, String userName, String password, String firstName, String lastName, String role,
            Boolean isActive) {
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
    public List<Phone> getPhones() {
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

    public Long getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(Long lastLogin) {
        this.lastLogin = lastLogin;
    }

    public Long getRegDate() {
        return regDate;
    }

    public void setRegDate(Long regDate) {
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

    @JsonIgnore
    @JsonbTransient
    public Secret getSecret() {
        return secret;
    }

    @JsonIgnore
    @JsonbTransient
    public void setSecret(Secret secret) {
        this.secret = secret;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public String getPhrase() {
        return phrase;
    }

    public void setPhrase(String phrase) {
        this.phrase = phrase;
    }

    public LocalDate getBirthday() {
        return birthday;
    }

    public void setBirthday(LocalDate birthday) {
        this.birthday = birthday;
    }

    public Integer getVisitedOps() {
        return visitedOps;
    }

    public void setVisitedOps(Integer visitedOps) {
        this.visitedOps = visitedOps;
    }

    public String getRibbon() {
        return ribbon;
    }

    public void setRibbon(String ribbon) {
        this.ribbon = ribbon;
    }

    public Boolean getYearlyFeePaid() {
        return yearlyFeePaid != null ? yearlyFeePaid : false;
    }

    public void setYearlyFeePaid(Boolean yearlyFeePaid) {
        this.yearlyFeePaid = yearlyFeePaid;
    }

    public Long getEventsAttended() {
        return eventsAttended;
    }

    public void setEventsAttended(Long eventsAttended) {
        this.eventsAttended = eventsAttended;
    }

    @JsonIgnore
    @JsonbTransient
    public User getMentor() {
        return mentor;
    }

    @JsonIgnore
    @JsonbTransient
    public void setMentor(User mentor) {
        this.mentor = mentor;
    }

    @JsonIgnore
    @JsonbTransient
    public List<User> getMentees() {
        return mentees;
    }

    @JsonIgnore
    @JsonbTransient
    public void setMentees(List<User> mentees) {
        this.mentees = mentees;
    }

    public String getMentorName() {
        return mentor != null ? mentor.getUserName() : null;
    }

    public List<String> getMentorOf() {
        List<String> list = new ArrayList<>();
        if (mentees != null) {
            for (User m : mentees) {
                list.add(m.getUserName());
            }
        }
        return list;
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
