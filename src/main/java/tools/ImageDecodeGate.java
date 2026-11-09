package tools;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Limits how many uploaded images are decoded at the same time. A decoded 40-megapixel image
 * needs about 160 MB plus its scaled copies, so unbounded parallel uploads can exhaust the heap.
 * Callers that cannot get a slot within a short wait answer 503 and the client may retry.
 */
@ApplicationScoped
public class ImageDecodeGate {

    @ConfigProperty(name = "images.decode.max-concurrent", defaultValue = "2")
    int maxConcurrent;

    @ConfigProperty(name = "images.decode.max-wait", defaultValue = "3s")
    java.time.Duration maxWait;

    private Semaphore permits;
    private long waitMillis;

    @jakarta.annotation.PostConstruct
    void init() {
        this.permits = new Semaphore(Math.max(1, maxConcurrent), true);
        this.waitMillis = Math.max(0, maxWait.toMillis());
    }

    @FunctionalInterface
    public interface Work {
        Response run() throws Exception;
    }

    /** Runs {@code work} while holding a decode slot, or returns 503 when none frees up in time. */
    public Response run(Work work) throws Exception {
        if (!acquire()) {
            return busy();
        }
        try {
            return work.run();
        } finally {
            permits.release();
        }
    }

    /**
     * Like {@link #run} for code that does not build a response itself.
     *
     * @throws jakarta.ws.rs.WebApplicationException with status 503 when no slot frees up in time
     */
    public <T> T call(java.util.concurrent.Callable<T> work) throws Exception {
        if (!acquire()) {
            throw new jakarta.ws.rs.WebApplicationException(busy());
        }
        try {
            return work.call();
        } finally {
            permits.release();
        }
    }

    private boolean acquire() {
        try {
            if (permits.tryAcquire(waitMillis, TimeUnit.MILLISECONDS)) {
                return true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Telemetry.warn("Image decoding busy, upload rejected", "image.decode.available", permits.availablePermits());
        return false;
    }

    private static Response busy() {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .header("Retry-After", "5")
                .entity("Gerade werden viele Bilder verarbeitet. Bitte versuche es in ein paar Sekunden erneut.")
                .build();
    }
}
