package resources;

import helper.AccountStatus;
import helper.AuthCookies;
import helper.OidcRoleMapper;
import helper.UserPrincipalResolver;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import model.User;
import model.Users.AuthSession;
import orm.AuthSessionOrm;
import orm.UserOrm;
import tools.AuditLogger;
import tools.OidcClient;
import tools.Telemetry;


/**
 * Browser sessions. Both login methods end in the same place: a short-lived
 * access JWT in the HttpOnly {@code wr_at} cookie and an opaque, rotating
 * refresh token in the HttpOnly {@code wr_rt} cookie (path {@code /auth}).
 * No token is ever returned in a response body.
 */
@Path("/auth")
@RequestScoped
public class AuthResource {

    @Inject UserOrm userOrm;
    @Inject AuthSessionOrm sessions;
    @Inject AuthCookies cookies;
    @Inject OidcClient oidcClient;
    @Inject OidcRoleMapper roleMapper;
    @Inject UserPrincipalResolver userPrincipalResolver;
    @Inject SecurityIdentity identity;

    public static class OidcCallback {
        public String code;
        public String codeVerifier;
    }

    @POST
    @Path("/login")
    @PermitAll
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response login(User credentials, @HeaderParam("User-Agent") String userAgent) {
        UserOrm.LoginResult result = userOrm.authenticateLocal(credentials);
        if (result.error() != null) {
            return result.error();
        }
        User user = result.user();
        AuditLogger.crud(user.getUserName(), user.getId(), "LOGIN", "Session", user.getId(), "method=local");
        String refreshToken = sessions.create(user.getId(), user.getTokenVersion(), AuthSession.METHOD_LOCAL,
                null, null, userAgent);
        return withSession(user, refreshToken);
    }

    @POST
    @Path("/oidc")
    @PermitAll
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response oidcLogin(OidcCallback callback, @HeaderParam("User-Agent") String userAgent) {
        if (callback == null || isBlank(callback.code) || isBlank(callback.codeVerifier)) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Code und Verifier fehlen.").build();
        }
        OidcClient.Tokens tokens;
        try {
            tokens = oidcClient.exchangeCode(callback.code, callback.codeVerifier);
        } catch (OidcClient.GrantRejectedException e) {
            Telemetry.warn("OIDC code exchange rejected", e, "auth.method", "oidc");
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("Anmeldung fehlgeschlagen. Bitte versuche es erneut.").build();
        } catch (Exception e) {
            Telemetry.error("OIDC code exchange failed", e, "auth.method", "oidc");
            return Response.status(Response.Status.BAD_GATEWAY)
                    .entity("Der Anmeldedienst ist gerade nicht erreichbar.").build();
        }

        UserOrm.OidcResolution resolution = userOrm.resolveOidcUser(profile(tokens), roleMapper.mapToRole(tokens.groups()));
        User user = resolution.user();
        if (user == null) {
            return Response.status(Response.Status.FORBIDDEN).entity(resolution.problem()).build();
        }
        if (!AccountStatus.isUsable(user)) {
            return denied();
        }
        userOrm.recordLogin(user);
        AuditLogger.crud(user.getUserName(), user.getId(), "LOGIN", "Session", user.getId(), "method=oidc");
        String refreshToken = sessions.create(user.getId(), user.getTokenVersion(), AuthSession.METHOD_OIDC,
                tokens.refreshToken(), tokens.accessExpiresAt(), userAgent);
        return withSession(user, refreshToken);
    }

    /**
     * Issues a new access token and rotates the refresh token. Without a refresh
     * cookie this is not an error: the visitor simply has no session.
     */
    @POST
    @Path("/refresh")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    public Response refresh(@CookieParam(AuthCookies.REFRESH_COOKIE) String refreshToken) {
        if (isBlank(refreshToken)) {
            return Response.ok(anonymous()).build();
        }
        AuthSessionOrm.Refresh refresh = sessions.rotate(refreshToken);
        if (refresh.outcome() != AuthSessionOrm.Outcome.ROTATED && refresh.outcome() != AuthSessionOrm.Outcome.GRACE) {
            return sessionEnded();
        }
        AuthSession session = refresh.session();

        if (refresh.outcome() == AuthSessionOrm.Outcome.ROTATED && !recheckIdentityProvider(session)) {
            sessions.revoke(session.getId());
            return sessionEnded();
        }

        User user = userOrm.findById(session.getUserId());
        if (user == null || user.getTokenVersion() != session.getTokenVersion()) {
            sessions.revoke(session.getId());
            return sessionEnded();
        }
        if (!AccountStatus.isUsable(user)) {
            sessions.revoke(session.getId());
            return denied();
        }
        // In the grace window another tab already received the rotated refresh cookie.
        return withSession(user, refresh.outcome() == AuthSessionOrm.Outcome.ROTATED ? refresh.newToken() : null);
    }

    /**
     * Ends this browser's session; {@code all=true} ends every session of the account.
     * Works from the refresh cookie alone, so an expired access token never blocks a logout.
     */
    @POST
    @Path("/logout")
    @PermitAll
    public Response logout(@CookieParam(AuthCookies.REFRESH_COOKIE) String refreshToken,
            @QueryParam("all") @DefaultValue("false") boolean all) {
        Long userId = sessions.revokeByToken(refreshToken);
        if (all && userId != null) {
            userOrm.revokeAllSessions(userId);
            sessions.revokeAllForUser(userId);
        }
        return Response.noContent().cookie(cookies.clearAccess(), cookies.clearRefresh()).build();
    }

    /** Who is logged in. Anonymous visitors get {@code authenticated: false}, not an error. */
    @GET
    @Path("/session")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    public Response session() {
        if (identity.isAnonymous()) {
            return Response.ok(anonymous()).build();
        }
        User user = userPrincipalResolver.resolveUser();
        if (user == null) {
            return Response.ok(anonymous()).build();
        }
        Long expiresAt = identity.getPrincipal() instanceof org.eclipse.microprofile.jwt.JsonWebToken jwt
                ? jwt.getExpirationTime() * 1000L : null;
        return Response.ok(sessionJson(user, expiresAt)).build();
    }

    /**
     * For Authentik sessions, re-checks the account at the identity provider once
     * its access token has expired and syncs the role. Returns false when the
     * provider rejects the session; an unreachable provider does not log users out.
     */
    private boolean recheckIdentityProvider(AuthSession session) {
        if (!AuthSession.METHOD_OIDC.equals(session.getLoginMethod())) return true;
        Long expiresAt = session.getIdpAccessExpiresAt();
        if (expiresAt != null && expiresAt > System.currentTimeMillis()) return true;
        String idpRefreshToken = sessions.decryptIdpRefreshToken(session);
        if (idpRefreshToken == null) return true;
        try {
            OidcClient.Tokens tokens = oidcClient.refresh(idpRefreshToken);
            sessions.updateIdpTokens(session.getId(), tokens.refreshToken(), tokens.accessExpiresAt());
            UserOrm.OidcResolution resolution = userOrm.resolveOidcUser(profile(tokens), roleMapper.mapToRole(tokens.groups()));
            return resolution.user() != null && resolution.user().getId().equals(session.getUserId());
        } catch (OidcClient.GrantRejectedException e) {
            Telemetry.event("oidc.session_ended_by_idp", "auth.session.id", session.getId(), "user.id", session.getUserId(), "reason", e.getMessage());
            return false;
        } catch (Exception e) {
            Telemetry.warn("Identity provider unreachable while refreshing session", e, "auth.session.id", session.getId());
            return true;
        }
    }

    private Response withSession(User user, String refreshToken) {
        String accessToken = JWT.generator(user, cookies.accessTokenTtl());
        long expiresAt = System.currentTimeMillis() + cookies.accessTokenTtl().toMillis();
        Response.ResponseBuilder response = Response.ok(sessionJson(user, expiresAt)).cookie(cookies.access(accessToken));
        if (refreshToken != null) {
            response.cookie(cookies.refresh(refreshToken));
        }
        return response.build();
    }

    private Response sessionEnded() {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity("Deine Sitzung ist abgelaufen. Bitte melde dich erneut an.")
                .cookie(cookies.clearAccess(), cookies.clearRefresh())
                .build();
    }

    private Response denied() {
        return Response.status(Response.Status.FORBIDDEN)
                .header(AccountStatus.STATUS_HEADER, AccountStatus.STATUS_DENIED)
                .entity(AccountStatus.BLOCKED_MESSAGE)
                .cookie(cookies.clearAccess(), cookies.clearRefresh())
                .build();
    }

    /** {@code accessExpiresAt} lets the frontend refresh before the access cookie lapses. */
    private JsonObject sessionJson(User user, Long accessExpiresAt) {
        JsonObjectBuilder builder = Json.createObjectBuilder()
                .add("authenticated", true)
                .add("userId", user.getId())
                .add("userName", user.getUserName())
                .add("role", user.getRole())
                .add("canCreateCategory", Boolean.TRUE.equals(user.getCanCreateCategory()))
                .add("oidcLinked", user.isOidcLinked());
        if (accessExpiresAt != null) {
            builder.add("accessExpiresAt", accessExpiresAt);
        }
        if (user.getPhotoUrl() != null) {
            builder.add("photoUrl", user.getPhotoUrl());
        } else {
            builder.addNull("photoUrl");
        }
        return builder.build();
    }

    private static JsonObject anonymous() {
        return Json.createObjectBuilder().add("authenticated", false).build();
    }

    private static UserOrm.OidcProfile profile(OidcClient.Tokens tokens) {
        return new UserOrm.OidcProfile(tokens.issuer(), tokens.subject(), tokens.preferredUsername(),
                tokens.email(), tokens.emailVerified(), tokens.givenName(), tokens.familyName());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
