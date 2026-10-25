package model.Users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Server-side half of a browser login. The browser only holds an opaque refresh
 * token in an HttpOnly cookie; this row stores its SHA-256 hash, never the token.
 * For Authentik logins the identity provider's refresh token is kept here,
 * AES-GCM encrypted, and never reaches the browser.
 *
 * The user id is a plain column (no foreign key) so that deleting an account is
 * never blocked by its sessions; refresh rejects sessions of unknown accounts.
 */
@Entity
@Table(name = "AUTH_SESSION", indexes = {
        @Index(name = "ix_auth_session_token", columnList = "token_hash", unique = true),
        @Index(name = "ix_auth_session_previous", columnList = "previous_token_hash"),
        @Index(name = "ix_auth_session_user", columnList = "user_id")
})
public class AuthSession {

    public static final String METHOD_LOCAL = "local";
    public static final String METHOD_OIDC = "oidc";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    /** Hash of the token this one replaced; presenting it again outside the grace window means reuse. */
    @Column(name = "previous_token_hash", length = 64)
    private String previousTokenHash;

    @Column(name = "rotated_at")
    private Long rotatedAt;

    /** {@link model.User#getTokenVersion()} at login; a later revocation ends the session. */
    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    @Column(name = "login_method", nullable = false, length = 16)
    private String loginMethod;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @Column(name = "last_used_at", nullable = false)
    private long lastUsedAt;

    @Column(name = "revoked_at")
    private Long revokedAt;

    @Column(name = "idp_refresh_token_encrypted", length = 4096)
    private String idpRefreshTokenEncrypted;

    /** When the identity provider's access token expires; after that refresh re-checks the account there. */
    @Column(name = "idp_access_expires_at")
    private Long idpAccessExpiresAt;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public String getPreviousTokenHash() { return previousTokenHash; }
    public void setPreviousTokenHash(String previousTokenHash) { this.previousTokenHash = previousTokenHash; }
    public Long getRotatedAt() { return rotatedAt; }
    public void setRotatedAt(Long rotatedAt) { this.rotatedAt = rotatedAt; }
    public int getTokenVersion() { return tokenVersion; }
    public void setTokenVersion(int tokenVersion) { this.tokenVersion = tokenVersion; }
    public String getLoginMethod() { return loginMethod; }
    public void setLoginMethod(String loginMethod) { this.loginMethod = loginMethod; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(long lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public Long getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Long revokedAt) { this.revokedAt = revokedAt; }
    public String getIdpRefreshTokenEncrypted() { return idpRefreshTokenEncrypted; }
    public void setIdpRefreshTokenEncrypted(String value) { this.idpRefreshTokenEncrypted = value; }
    public Long getIdpAccessExpiresAt() { return idpAccessExpiresAt; }
    public void setIdpAccessExpiresAt(Long idpAccessExpiresAt) { this.idpAccessExpiresAt = idpAccessExpiresAt; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
}
