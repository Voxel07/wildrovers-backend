package model.notifications;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "NOTIFICATION_ITEM", uniqueConstraints = @UniqueConstraint(
        name = "uq_notification_item_key", columnNames = "idempotency_key"))
public class NotificationItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "resource_type", nullable = false, length = 20)
    private String resourceType;
    @Column(name = "action_type", nullable = false, length = 30)
    private String actionType;
    @Column(name = "entity_id", nullable = false)
    private Long entityId;
    @Column(name = "idempotency_key", nullable = false, length = 180)
    private String idempotencyKey;
    @Column(name = "title", nullable = false, length = 500)
    private String title;
    @Column(name = "target_url", length = 1500)
    private String targetUrl;
    @Column(name = "occurred_at", nullable = false)
    private long occurredAt;
    @Column(name = "event_start_at")
    private Long eventStartAt;
    @Column(name = "requires_response", nullable = false)
    private boolean requiresResponse;
    /** Minimum application role allowed to learn about this item (e.g. forum category visibility). */
    @Column(name = "required_role", length = 20)
    private String requiredRole;

    public Long getId() { return id; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getTargetUrl() { return targetUrl; }
    public void setTargetUrl(String targetUrl) { this.targetUrl = targetUrl; }
    public long getOccurredAt() { return occurredAt; }
    public void setOccurredAt(long occurredAt) { this.occurredAt = occurredAt; }
    public Long getEventStartAt() { return eventStartAt; }
    public void setEventStartAt(Long eventStartAt) { this.eventStartAt = eventStartAt; }
    public boolean isRequiresResponse() { return requiresResponse; }
    public void setRequiresResponse(boolean requiresResponse) { this.requiresResponse = requiresResponse; }
    public String getRequiredRole() { return requiredRole == null || requiredRole.isBlank() ? model.Users.Roles.VSISITOR : requiredRole; }
    public void setRequiredRole(String requiredRole) { this.requiredRole = requiredRole; }
}
