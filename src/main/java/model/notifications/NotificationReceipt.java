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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import model.User;

@Entity
@Table(name = "NOTIFICATION_RECEIPT", uniqueConstraints = @UniqueConstraint(
        name = "uq_notification_receipt_user_item", columnNames = { "user_id", "item_id" }))
public class NotificationReceipt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @JsonIgnore @JsonbTransient
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private NotificationItem item;
    @Column(name = "email_requested", nullable = false)
    private boolean emailRequested;
    @Column(name = "webhook_requested", nullable = false)
    private boolean webhookRequested;
    @Column(name = "email_last_sent_at")
    private Long emailLastSentAt;
    @Column(name = "webhook_last_sent_at")
    private Long webhookLastSentAt;
    @Column(name = "email_next_attempt_at")
    private Long emailNextAttemptAt;
    @Column(name = "webhook_next_attempt_at")
    private Long webhookNextAttemptAt;
    @Column(name = "webhook_attempts", nullable = false)
    private int webhookAttempts;
    @Column(name = "acknowledged_at")
    private Long acknowledgedAt;

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public NotificationItem getItem() { return item; }
    public void setItem(NotificationItem item) { this.item = item; }
    public boolean isEmailRequested() { return emailRequested; }
    public void setEmailRequested(boolean emailRequested) { this.emailRequested = emailRequested; }
    public boolean isWebhookRequested() { return webhookRequested; }
    public void setWebhookRequested(boolean webhookRequested) { this.webhookRequested = webhookRequested; }
    public Long getEmailLastSentAt() { return emailLastSentAt; }
    public void setEmailLastSentAt(Long emailLastSentAt) { this.emailLastSentAt = emailLastSentAt; }
    public Long getWebhookLastSentAt() { return webhookLastSentAt; }
    public void setWebhookLastSentAt(Long webhookLastSentAt) { this.webhookLastSentAt = webhookLastSentAt; }
    public Long getEmailNextAttemptAt() { return emailNextAttemptAt; }
    public void setEmailNextAttemptAt(Long emailNextAttemptAt) { this.emailNextAttemptAt = emailNextAttemptAt; }
    public Long getWebhookNextAttemptAt() { return webhookNextAttemptAt; }
    public void setWebhookNextAttemptAt(Long webhookNextAttemptAt) { this.webhookNextAttemptAt = webhookNextAttemptAt; }
    public int getWebhookAttempts() { return webhookAttempts; }
    public void setWebhookAttempts(int webhookAttempts) { this.webhookAttempts = webhookAttempts; }
    public Long getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(Long acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
}
