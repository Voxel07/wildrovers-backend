package helper;

import jakarta.enterprise.context.RequestScoped;

/**
 * Captures the client IP address for the current request.
 * Populated by RateLimitFilter at the start of every request.
 */
@RequestScoped
public class RequestIpCapture {

    private String clientIp;

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }
}
