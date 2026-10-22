package helper;

import model.User;

/**
 * Central definition of when an account may use the API and the identity
 * attributes the authentication layer attaches for {@link UserPrincipalResolver}.
 */
public final class AccountStatus {

    /** Database id of the account bound to the authenticated token (Long). */
    public static final String USER_ID_ATTRIBUTE = "wildrovers.user-id";

    /** Set (String message) when the token is valid but the account must not be used. */
    public static final String DENIED_ATTRIBUTE = "wildrovers.account-denied";

    public static final String BLOCKED_MESSAGE = "Dein Account wurde gesperrt.";

    /**
     * Response header that marks a 403 as "this account may not use the API" (blocked/inactive),
     * as opposed to an ordinary permission denial. The frontend ends the session only on this.
     */
    public static final String STATUS_HEADER = "X-Account-Status";
    public static final String STATUS_DENIED = "denied";

    private AccountStatus() { }

    public static boolean isUsable(User user) {
        return user != null && user.isActive() && !user.getIsBlocked() && user.getRole() != null;
    }
}
