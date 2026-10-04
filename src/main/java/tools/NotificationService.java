package tools;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import model.Event;
import model.User;
import model.Users.Roles;
import model.notifications.NotificationItem;
import model.notifications.NotificationPreference;
import model.notifications.NotificationReceipt;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class NotificationService {
    private static final long WEEK = 7L * 24 * 60 * 60 * 1000;
    @Inject
    EntityManager em;

    /** Minimum role per resource type when the caller has no finer-grained visibility. */
    public static String defaultRequiredRole(String resourceType) {
        return switch (resourceType) {
            case "SIGNUP" -> Roles.ALDERMEN;
            case "GALLERY" -> Roles.FRESHMAN; // albums are team-only
            default -> Roles.VSISITOR;
        };
    }

    @Transactional
    public void record(String resourceType, String action, Long entityId, String title, String targetUrl,
            Long eventStartAt, boolean requiresResponse, Long actorUserId) {
        record(resourceType, action, entityId, title, targetUrl, eventStartAt, requiresResponse, actorUserId,
                defaultRequiredRole(resourceType));
    }

    /**
     * Records an item and fans it out to subscribers. Only active, unblocked accounts whose
     * role satisfies {@code requiredRole} receive a receipt; delivery re-checks this.
     */
    @Transactional
    public void record(String resourceType, String action, Long entityId, String title, String targetUrl,
            Long eventStartAt, boolean requiresResponse, Long actorUserId, String requiredRole) {
        String audience = requiredRole == null || requiredRole.isBlank() ? Roles.VSISITOR : requiredRole;
        long now = System.currentTimeMillis();
        String key = resourceType + ":" + action + ":" + entityId + ("UPDATED".equals(action) ? ":" + now : "");
        Long existing = em
                .createQuery("SELECT COUNT(i) FROM NotificationItem i WHERE i.idempotencyKey = :key", Long.class)
                .setParameter("key", key).getSingleResult();
        if (existing > 0)
            return;

        NotificationItem item = new NotificationItem();
        item.setResourceType(resourceType);
        item.setActionType(action);
        item.setEntityId(entityId);
        item.setIdempotencyKey(key);
        item.setTitle(title == null || title.isBlank() ? resourceType : title);
        item.setTargetUrl(targetUrl);
        item.setOccurredAt(now);
        item.setEventStartAt(eventStartAt);
        item.setRequiresResponse(requiresResponse);
        item.setRequiredRole(audience);
        em.persist(item);

        List<NotificationPreference> subscribers = em.createQuery(
                "SELECT p FROM NotificationPreference p JOIN FETCH p.user u "
                        + "WHERE p.resourceType = :type AND (p.emailEnabled = true OR p.webhookEnabled = true) "
                        + "AND u.role IN :roles AND u.isActive = true AND (u.isBlocked IS NULL OR u.isBlocked = false)",
                NotificationPreference.class)
                .setParameter("type", resourceType)
                .setParameter("roles", rolesAtLeast(audience))
                .getResultList();
        for (NotificationPreference preference : subscribers) {
            if (actorUserId != null && actorUserId.equals(preference.getUser().getId()))
                continue;
            NotificationReceipt receipt = new NotificationReceipt();
            receipt.setUser(preference.getUser());
            receipt.setItem(item);
            receipt.setEmailRequested(preference.isEmailEnabled());
            receipt.setWebhookRequested(preference.isWebhookEnabled());
            receipt.setEmailNextAttemptAt("EVENT".equals(resourceType) ? now : nextDigestAt(now));
            receipt.setWebhookNextAttemptAt(now);
            em.persist(receipt);
        }
    }

    @Transactional
    public void recordEvent(Event event, String action, Long actorUserId, String frontendUrl) {
        Long eventStart = event.getEventDate() == null ? null
                : event.getEventDate().atZone(ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli();
        record("EVENT", action, event.getId(), event.getTitle(), frontendUrl + "/events", eventStart,
                "CREATED".equals(action) || "UPDATED".equals(action), actorUserId);
    }

    @Transactional
    public void recordSignup(User user, String frontendUrl) {
        record("SIGNUP", "CREATED", user.getId(), "Neue Registrierung: " + user.getUserName(),
                frontendUrl + "/Admin/UserManagement", null, false, user.getId());
    }

    @Transactional
    public void acknowledgeEvent(Long userId, Long eventId) {
        long now = System.currentTimeMillis();
        em.createQuery(
                "UPDATE NotificationReceipt r SET r.acknowledgedAt = :now WHERE r.user.id = :userId AND r.item.resourceType = 'EVENT' AND r.item.entityId = :eventId")
                .setParameter("now", now).setParameter("userId", userId).setParameter("eventId", eventId)
                .executeUpdate();
    }

    @Transactional
    public int acknowledgeDigest(Long userId) {
        return em.createQuery(
                "UPDATE NotificationReceipt r SET r.acknowledgedAt = :now WHERE r.user.id = :userId AND r.item.resourceType IN ('FORUM','GALLERY','SIGNUP') AND r.acknowledgedAt IS NULL")
                .setParameter("now", System.currentTimeMillis()).setParameter("userId", userId).executeUpdate();
    }

    @Transactional
    public List<DeliveryBatch> dueEventBatches(String channel) {
        long now = System.currentTimeMillis();
        String lastField = "EMAIL".equals(channel) ? "r.emailLastSentAt" : "r.webhookLastSentAt";
        String nextField = "EMAIL".equals(channel) ? "r.emailNextAttemptAt" : "r.webhookNextAttemptAt";
        String requested = "EMAIL".equals(channel) ? "r.emailRequested" : "r.webhookRequested";
        String jpql = "SELECT r FROM NotificationReceipt r JOIN FETCH r.user JOIN FETCH r.item i WHERE i.resourceType = 'EVENT' "
                + "AND r.acknowledgedAt IS NULL AND " + requested + " = true AND (" + nextField + " IS NULL OR "
                + nextField + " <= :now) "
                + "AND (" + lastField + " IS NULL OR (i.requiresResponse = true AND " + lastField
                + " <= :weekly AND i.eventStartAt > :now "
                + "AND NOT EXISTS (SELECT a.id FROM EventAttendance a WHERE a.event.id = i.entityId AND a.user.id = r.user.id))) ORDER BY i.occurredAt";
        List<NotificationReceipt> receipts = em.createQuery(jpql, NotificationReceipt.class)
                .setParameter("now", now).setParameter("weekly", now - WEEK).setMaxResults(20).getResultList();
        List<DeliveryBatch> result = new ArrayList<>();
        for (NotificationReceipt receipt : receipts) {
            if (!mayReceive(receipt)) {
                retire(receipt, channel);
                continue;
            }
            if (currentlyEnabled(receipt.getUser().getId(), "EVENT", channel))
                result.add(batch(receipt.getUser(), List.of(receipt)));
        }
        return result;
    }

    @Transactional
    public List<DeliveryBatch> dueDigestBatches(String channel) {
        long now = System.currentTimeMillis();
        String lastField = "EMAIL".equals(channel) ? "r.emailLastSentAt" : "r.webhookLastSentAt";
        String nextField = "EMAIL".equals(channel) ? "r.emailNextAttemptAt" : "r.webhookNextAttemptAt";
        String requested = "EMAIL".equals(channel) ? "r.emailRequested" : "r.webhookRequested";
        String jpql = "SELECT DISTINCT r.user.id FROM NotificationReceipt r WHERE r.item.resourceType IN ('FORUM','GALLERY','SIGNUP') "
                + "AND r.acknowledgedAt IS NULL AND " + requested + " = true AND (" + nextField + " IS NULL OR "
                + nextField + " <= :now) "
                + "AND (" + lastField + " IS NULL OR " + lastField + " <= :weekly)";
        List<Long> userIds = em.createQuery(jpql, Long.class).setParameter("now", now)
                .setParameter("weekly", now - WEEK).getResultList();
        List<DeliveryBatch> result = new ArrayList<>();
        for (Long userId : userIds) {
            List<NotificationReceipt> receipts = em.createQuery(
                    "SELECT r FROM NotificationReceipt r JOIN FETCH r.user JOIN FETCH r.item i WHERE r.user.id = :userId AND i.resourceType IN ('FORUM','GALLERY','SIGNUP') AND r.acknowledgedAt IS NULL AND "
                            + requested + " = true AND (" + lastField + " IS NOT NULL OR " + nextField
                            + " <= :now) ORDER BY i.occurredAt",
                    NotificationReceipt.class).setParameter("userId", userId).setParameter("now", now).getResultList();
            receipts.removeIf(r -> {
                if (!mayReceive(r)) {
                    retire(r, channel);
                    return true;
                }
                return !currentlyEnabled(userId, r.getItem().getResourceType(), channel);
            });
            if (!receipts.isEmpty())
                result.add(batch(receipts.get(0).getUser(), receipts));
        }
        return result;
    }

    @Transactional
    public void markDelivered(List<Long> receiptIds, String channel) {
        if (receiptIds.isEmpty())
            return;
        long now = System.currentTimeMillis();
        String field = "EMAIL".equals(channel) ? "r.emailLastSentAt" : "r.webhookLastSentAt";
        String next = "EMAIL".equals(channel) ? "r.emailNextAttemptAt" : "r.webhookNextAttemptAt";
        em.createQuery("UPDATE NotificationReceipt r SET " + field + " = :now, " + next + " = NULL WHERE r.id IN :ids")
                .setParameter("now", now).setParameter("ids", receiptIds).executeUpdate();
    }

    @Transactional
    public void markRetry(List<Long> receiptIds, String channel, int attempts) {
        if (receiptIds.isEmpty())
            return;
        long delay = switch (Math.min(attempts, 5)) {
            case 0 -> 60_000L;
            case 1 -> 300_000L;
            case 2 -> 1_800_000L;
            case 3 -> 7_200_000L;
            default -> 43_200_000L;
        };
        String next = "EMAIL".equals(channel) ? "r.emailNextAttemptAt" : "r.webhookNextAttemptAt";
        em.createQuery("UPDATE NotificationReceipt r SET " + next + " = :next WHERE r.id IN :ids")
                .setParameter("next", System.currentTimeMillis() + delay).setParameter("ids", receiptIds)
                .executeUpdate();
        if ("WEBHOOK".equals(channel)) {
            em.createQuery(
                    "UPDATE NotificationReceipt r SET r.webhookAttempts = r.webhookAttempts + 1 WHERE r.id IN :ids")
                    .setParameter("ids", receiptIds).executeUpdate();
        }
    }

    /** Account status and permissions are re-checked at delivery time (they may have changed). */
    private static boolean mayReceive(NotificationReceipt receipt) {
        User user = receipt.getUser();
        return helper.AccountStatus.isUsable(user)
                && Roles.hasRequiredRole(user.getRole(), receipt.getItem().getRequiredRole());
    }

    /** Stops delivering a receipt on a channel so it no longer occupies the delivery queue. */
    private static void retire(NotificationReceipt receipt, String channel) {
        if ("EMAIL".equals(channel)) {
            receipt.setEmailRequested(false);
        } else {
            receipt.setWebhookRequested(false);
        }
    }

    private static List<String> rolesAtLeast(String requiredRole) {
        return Roles.getRoles().stream().filter(role -> Roles.hasRequiredRole(role, requiredRole)).toList();
    }

    private boolean currentlyEnabled(Long userId, String resource, String channel) {
        String field = "EMAIL".equals(channel) ? "p.emailEnabled" : "p.webhookEnabled";
        Long count = em.createQuery(
                "SELECT COUNT(p) FROM NotificationPreference p WHERE p.user.id = :userId AND p.resourceType = :resource AND "
                        + field + " = true",
                Long.class)
                .setParameter("userId", userId).setParameter("resource", resource).getSingleResult();
        return count > 0;
    }

    private DeliveryBatch batch(User user, List<NotificationReceipt> receipts) {
        List<DeliveryItem> items = receipts.stream()
                .map(r -> new DeliveryItem(r.getItem().getId(), r.getItem().getResourceType(),
                        r.getItem().getActionType(), r.getItem().getEntityId(), r.getItem().getTitle(),
                        r.getItem().getTargetUrl(),
                        r.getItem().getOccurredAt(), r.getItem().isRequiresResponse()))
                .toList();
        return new DeliveryBatch(user.getId(), user.getEmail(), user.getUserName(),
                receipts.stream().map(NotificationReceipt::getId).toList(), items,
                receipts.stream().mapToInt(NotificationReceipt::getWebhookAttempts).max().orElse(0));
    }

    private long nextDigestAt(long now) {
        ZoneId zone = ZoneId.of("Europe/Berlin");
        ZonedDateTime current = java.time.Instant.ofEpochMilli(now).atZone(zone);
        ZonedDateTime next = current.withHour(6).withMinute(0).withSecond(0).withNano(0);
        if (!next.isAfter(current))
            next = next.plusDays(1);
        return next.toInstant().toEpochMilli();
    }

    public record DeliveryBatch(Long userId, String email, String username, List<Long> receiptIds,
            List<DeliveryItem> items, int attempts) {
    }

    public record DeliveryItem(Long id, String resource, String action, Long entityId, String title, String url,
            long occurredAt, boolean requiresResponse) {
    }
}
