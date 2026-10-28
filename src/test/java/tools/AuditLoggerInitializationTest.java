package tools;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuditLoggerInitializationTest {

    @Test
    void classInitializationDoesNotRegisterGlobalOpenTelemetry() throws Exception {
        String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        URL[] urls = Arrays.stream(classPath.split(File.pathSeparator))
                .map(Path::of)
                .map(path -> {
                    try {
                        return path.toUri().toURL();
                    } catch (java.net.MalformedURLException e) {
                        throw new IllegalArgumentException(e);
                    }
                }).toArray(URL[]::new);

        // A separate loader models native-image class initialization before Quarkus
        // starts, without changing the SDK used by other tests in this JVM.
        try (URLClassLoader loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
            Class<?> global = Class.forName("io.opentelemetry.api.GlobalOpenTelemetry", true, loader);
            assertEquals(false, global.getMethod("isSet").invoke(null));

            Class.forName("tools.AuditLogger", true, loader);

            assertEquals(false, global.getMethod("isSet").invoke(null),
                    "Loading AuditLogger must leave SDK registration to Quarkus at runtime");
        }
    }
}
