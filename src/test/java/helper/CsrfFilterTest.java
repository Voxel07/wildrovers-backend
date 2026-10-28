package helper;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
class CsrfFilterTest {
    private static final String ORIGINS = "quarkus.http.cors.origins";

    @Inject CsrfFilter filter;

    @Test
    void usesRuntimeOriginsInsteadOfKeepingTheInitialValue() {
        String previous = System.getProperty(ORIGINS);
        try {
            System.setProperty(ORIGINS, "http://localhost:5173");
            assertNull(checkOrigin("http://localhost:5173"));

            // The same CDI filter must use deployment config after initialization.
            System.setProperty(ORIGINS, "https://www.wild-rovers.de,https://wild-rovers.de");
            assertNull(checkOrigin("https://www.wild-rovers.de"));
            assertNull(checkOrigin("https://wild-rovers.de"));
            assertEquals(403, checkOrigin("http://localhost:5173").getStatus());
            assertEquals(403, checkOrigin("https://evil.example").getStatus());
        } finally {
            if (previous == null) {
                System.clearProperty(ORIGINS);
            } else {
                System.setProperty(ORIGINS, previous);
            }
        }
    }

    private Response checkOrigin(String origin) {
        AtomicReference<Response> rejected = new AtomicReference<>();
        UriInfo uri = (UriInfo) Proxy.newProxyInstance(UriInfo.class.getClassLoader(),
                new Class<?>[]{UriInfo.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getPath")) return "auth/refresh";
                    throw new UnsupportedOperationException(method.getName());
                });
        ContainerRequestContext request = (ContainerRequestContext) Proxy.newProxyInstance(
                ContainerRequestContext.class.getClassLoader(), new Class<?>[]{ContainerRequestContext.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMethod" -> "POST";
                    case "getCookies" -> Map.of();
                    case "getUriInfo" -> uri;
                    case "getHeaderString" -> switch ((String) args[0]) {
                        case "Origin" -> origin;
                        case CsrfFilter.HEADER -> "test";
                        default -> null;
                    };
                    case "abortWith" -> {
                        rejected.set((Response) args[0]);
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        filter.filter(request);
        return rejected.get();
    }
}
