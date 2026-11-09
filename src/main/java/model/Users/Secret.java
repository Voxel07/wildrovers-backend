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
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.json.bind.annotation.JsonbTransient;

@Entity
@Table(name = "SECRET")
public class Secret {

    @Id
    @SequenceGenerator(name = "KeysSeq", sequenceName = "ZSEQ_KEYS_ID", allocationSize = 1, initialValue = 1)
    @GeneratedValue(generator = "KeysSeq")
    
    @Column(name = "id", unique = true)
    private Long id;

    @Column(name = "password")
    private String password;

    @Column(name = "isVerifyed")
    Boolean isVerifyed;

    @Column(name = "verificationId") 
    String verificationId;

    @Column(name = "verificationTimestamp")
    private Long verificationTimestamp;

    @Column(name = "resetToken")
    private String resetToken;

    @Column(name = "resetTokenTimestamp")
    private Long resetTokenTimestamp;

    /** Wrong codes entered for the current verification code; the code is void after {@link #MAX_CODE_ATTEMPTS}. */
    @Column(name = "verificationAttempts", nullable = false, columnDefinition = "integer default 0")
    private Integer verificationAttempts = 0;

    /** Address the user asked to switch to; stored as e-mail only once the code sent there is confirmed. */
    @Column(name = "pendingEmail", length = 320)
    private String pendingEmail;

    /** SHA-256 of the confirmation code sent to {@link #pendingEmail}. */
    @Column(name = "pendingEmailCode", length = 64)
    private String pendingEmailCode;

    @Column(name = "pendingEmailTimestamp")
    private Long pendingEmailTimestamp;

    @Column(name = "pendingEmailAttempts", nullable = false, columnDefinition = "integer default 0")
    private Integer pendingEmailAttempts = 0;

    public static final int MAX_CODE_ATTEMPTS = 5;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;

    public Secret(){

    }

    @JsonIgnore
    @JsonbTransient
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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

    @JsonIgnore
    @JsonbTransient
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

    public Long getVerificationTimestamp() {
        return verificationTimestamp;
    }

    public void setVerificationTimestamp(Long verificationTimestamp) {
        this.verificationTimestamp = verificationTimestamp;
    }

    @JsonIgnore
    @JsonbTransient
    public String getResetToken() {
        return resetToken;
    }

    public void setResetToken(String resetToken) {
        this.resetToken = resetToken;
    }

    public Long getResetTokenTimestamp() {
        return resetTokenTimestamp;
    }

    public void setResetTokenTimestamp(Long resetTokenTimestamp) {
        this.resetTokenTimestamp = resetTokenTimestamp;
    }

    @JsonIgnore
    @JsonbTransient
    public int getVerificationAttempts() {
        return verificationAttempts != null ? verificationAttempts : 0;
    }

    public void setVerificationAttempts(Integer verificationAttempts) {
        this.verificationAttempts = verificationAttempts;
    }

    @JsonIgnore
    @JsonbTransient
    public String getPendingEmail() {
        return pendingEmail;
    }

    public void setPendingEmail(String pendingEmail) {
        this.pendingEmail = pendingEmail;
    }

    @JsonIgnore
    @JsonbTransient
    public String getPendingEmailCode() {
        return pendingEmailCode;
    }

    public void setPendingEmailCode(String pendingEmailCode) {
        this.pendingEmailCode = pendingEmailCode;
    }

    @JsonIgnore
    @JsonbTransient
    public Long getPendingEmailTimestamp() {
        return pendingEmailTimestamp;
    }

    public void setPendingEmailTimestamp(Long pendingEmailTimestamp) {
        this.pendingEmailTimestamp = pendingEmailTimestamp;
    }

    @JsonIgnore
    @JsonbTransient
    public int getPendingEmailAttempts() {
        return pendingEmailAttempts != null ? pendingEmailAttempts : 0;
    }

    public void setPendingEmailAttempts(Integer pendingEmailAttempts) {
        this.pendingEmailAttempts = pendingEmailAttempts;
    }

    /** Drops a pending e-mail change. */
    public void clearPendingEmail() {
        this.pendingEmail = null;
        this.pendingEmailCode = null;
        this.pendingEmailTimestamp = null;
        this.pendingEmailAttempts = 0;
    }
}
