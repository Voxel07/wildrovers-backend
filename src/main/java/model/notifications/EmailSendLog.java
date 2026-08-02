package model.notifications;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "EMAIL_SEND_LOG")
public class EmailSendLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "recipient", nullable = false, length = 320)
    private String recipient;
    @Column(name = "mail_type", nullable = false, length = 50)
    private String mailType;
    @Column(name = "status", nullable = false, length = 20)
    private String status;
    @Column(name = "created_at", nullable = false)
    private long createdAt;
    @Column(name = "completed_at")
    private Long completedAt;

    public Long getId() { return id; }
    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public String getMailType() { return mailType; }
    public void setMailType(String mailType) { this.mailType = mailType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public Long getCompletedAt() { return completedAt; }
    public void setCompletedAt(Long completedAt) { this.completedAt = completedAt; }
}
