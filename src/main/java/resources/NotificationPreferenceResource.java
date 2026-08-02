package resources;

import helper.UserPrincipalResolver;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import model.User;
import model.notifications.NotificationPreference;
import model.notifications.UserWebhook;
import tools.NotificationPreferenceService;
import tools.NotificationService;
import tools.WebhookCrypto;
import tools.WebhookSender;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Path("/user/me/notifications")
@RequestScoped
@RolesAllowed({ "Besucher", "Frischling", "Mitglied", "Vorstand", "Admin" })
@Produces(MediaType.APPLICATION_JSON)
public class NotificationPreferenceResource {
    @Inject UserPrincipalResolver resolver;
    @Inject NotificationPreferenceService service;
    @Inject WebhookCrypto crypto;
    @Inject WebhookSender sender;
    @Inject NotificationService notifications;

    @GET
    public Response get() {
        User user = resolver.resolveUser();
        return user == null ? Response.status(401).build() : Response.ok(response(user.getId(), null)).build();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response save(PreferenceRequest request) {
        User user = resolver.resolveUser();
        if (user == null) return Response.status(401).build();
        try {
            Map<String, NotificationPreferenceService.ChannelSelection> selections = new LinkedHashMap<>();
            for (String type : NotificationPreferenceService.RESOURCE_TYPES) {
                ChannelRequest channel = request != null && request.resources != null ? request.resources.get(type) : null;
                selections.put(type, new NotificationPreferenceService.ChannelSelection(channel != null && channel.email, channel != null && channel.webhook));
            }
            String secret = service.save(user.getId(), selections, request == null ? null : request.webhookUrl);
            return Response.ok(response(user.getId(), secret)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(400).entity(Map.of("message", e.getMessage())).build();
        }
    }

    @POST @Path("/webhook/test")
    public Response testWebhook() {
        User user = resolver.resolveUser();
        if (user == null) return Response.status(401).build();
        NotificationPreferenceService.WebhookTarget target = service.target(user.getId());
        if (target == null) return Response.status(400).entity(Map.of("message", "Kein aktiver Webhook konfiguriert")).build();
        String delivery = UUID.randomUUID().toString();
        String body = "{\"id\":\"" + delivery + "\",\"type\":\"webhook.test\",\"message\":\"Wild Rovers Webhook funktioniert\"}";
        try {
            int status = sender.send(target.url(), target.secret(), "webhook.test", body, delivery);
            boolean success = status >= 200 && status < 300;
            service.recordWebhookResult(target.id(), success, "HTTP " + status);
            return success ? Response.ok(Map.of("message", "Webhook erfolgreich getestet")).build()
                    : Response.status(502).entity(Map.of("message", "Webhook antwortete mit HTTP " + status)).build();
        } catch (Exception e) {
            service.recordWebhookResult(target.id(), false, e.getMessage());
            return Response.status(502).entity(Map.of("message", "Webhook-Test fehlgeschlagen")).build();
        }
    }

    @POST @Path("/webhook/rotate-secret")
    public Response rotateSecret() {
        User user = resolver.resolveUser();
        if (user == null) return Response.status(401).build();
        try { return Response.ok(Map.of("webhookSecret", service.rotateSecret(user.getId()))).build(); }
        catch (IllegalArgumentException e) { return Response.status(400).entity(Map.of("message", e.getMessage())).build(); }
    }

    @POST @Path("/acknowledge")
    public Response acknowledgeDigest() {
        User user = resolver.resolveUser();
        if (user == null) return Response.status(401).build();
        return Response.ok(Map.of("acknowledged", notifications.acknowledgeDigest(user.getId()))).build();
    }

    @DELETE @Path("/webhook")
    public Response deleteWebhook() {
        User user = resolver.resolveUser();
        if (user == null) return Response.status(401).build();
        service.deleteWebhook(user.getId());
        return Response.noContent().build();
    }

    private PreferenceResponse response(Long userId, String secret) {
        PreferenceResponse result = new PreferenceResponse();
        for (String type : NotificationPreferenceService.RESOURCE_TYPES) result.resources.put(type, new ChannelResponse());
        for (NotificationPreference preference : service.getPreferences(userId)) {
            ChannelResponse channel = result.resources.get(preference.getResourceType());
            if (channel != null) { channel.email = preference.isEmailEnabled(); channel.webhook = preference.isWebhookEnabled(); }
        }
        UserWebhook webhook = service.getWebhook(userId);
        if (webhook != null) {
            result.webhook.configured = true;
            result.webhook.enabled = webhook.isEnabled();
            result.webhook.urlMasked = maskUrl(crypto.decrypt(webhook.getUrlEncrypted()));
            result.webhook.verifiedAt = webhook.getVerifiedAt();
            result.webhook.lastSuccessAt = webhook.getLastSuccessAt();
            result.webhook.lastError = webhook.getLastError();
            result.webhook.failureCount = webhook.getFailureCount();
        }
        result.webhookSecret = secret;
        return result;
    }

    private String maskUrl(String value) {
        int slash = value.indexOf('/', value.indexOf("//") + 2);
        return slash < 0 ? value : value.substring(0, slash) + "/****";
    }

    public static class PreferenceRequest { public Map<String, ChannelRequest> resources = new LinkedHashMap<>(); public String webhookUrl; }
    public static class ChannelRequest { public boolean email; public boolean webhook; }
    public static class PreferenceResponse { public Map<String, ChannelResponse> resources = new LinkedHashMap<>(); public WebhookResponse webhook = new WebhookResponse(); public String webhookSecret; }
    public static class ChannelResponse { public boolean email; public boolean webhook; }
    public static class WebhookResponse { public boolean configured; public boolean enabled; public String urlMasked; public Long verifiedAt; public Long lastSuccessAt; public String lastError; public int failureCount; }
}
