package model.notifications;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.json.bind.annotation.JsonbTransient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import model.User;

@Entity
@Table(name = "USER_WEBHOOK", uniqueConstraints = @UniqueConstraint(name = "uq_user_webhook_user", columnNames = "user_id"))
public class UserWebhook {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @JsonbTransient
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @JsonIgnore
    @JsonbTransient
    @Column(name = "url_encrypted", nullable = false, length = 2048)
    private String urlEncrypted;

    @JsonIgnore
    @JsonbTransient
    @Column(name = "secret_encrypted", nullable = false, length = 1024)
    private String secretEncrypted;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;
    @Column(name = "verified_at")
    private Long verifiedAt;
    @Column(name = "failure_count", nullable = false)
    private int failureCount;
    @Column(name = "disabled_until")
    private Long disabledUntil;
    @Column(name = "last_success_at")
    private Long lastSuccessAt;
    @Column(name = "last_failure_at")
    private Long lastFailureAt;
    @Column(name = "last_error", length = 500)
    private String lastError;

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getUrlEncrypted() { return urlEncrypted; }
    public void setUrlEncrypted(String urlEncrypted) { this.urlEncrypted = urlEncrypted; }
    public String getSecretEncrypted() { return secretEncrypted; }
    public void setSecretEncrypted(String secretEncrypted) { this.secretEncrypted = secretEncrypted; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Long getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Long verifiedAt) { this.verifiedAt = verifiedAt; }
    public int getFailureCount() { return failureCount; }
    public void setFailureCount(int failureCount) { this.failureCount = failureCount; }
    public Long getDisabledUntil() { return disabledUntil; }
    public void setDisabledUntil(Long disabledUntil) { this.disabledUntil = disabledUntil; }
    public Long getLastSuccessAt() { return lastSuccessAt; }
    public void setLastSuccessAt(Long lastSuccessAt) { this.lastSuccessAt = lastSuccessAt; }
    public Long getLastFailureAt() { return lastFailureAt; }
    public void setLastFailureAt(Long lastFailureAt) { this.lastFailureAt = lastFailureAt; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}
