package co.edu.escuelaing.webframework;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public final class WebFramework {

    private static final int DEFAULT_PORT = 8080;

    private static final Router router = new Router();
    private static final StaticFileService staticFileService = new StaticFileService();
    private static final HttpServer server = new HttpServer(router, staticFileService, threadsFromEnv());

    private WebFramework() {
    }

    static int resolvePort(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_PORT;
        }
        int port;
        try {
            port = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("PORT must be a number, got '" + value + "'");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("PORT must be between 1 and 65535, got " + port);
        }
        return port;
    }

    private static int threadsFromEnv() {
        String value = System.getenv("THREADS");
        if (value == null || value.isBlank()) {
            return 16;
        }
        return Integer.parseInt(value.trim());
    }

    public static void staticfiles(String root) {
        staticFileService.setStaticFilesRoot(root);
    }

    public static void get(String path, Route.Service handler) {
        router.addRoute("GET", path, handler);
    }

    /**
     * Starts the server on the port given by the PORT environment variable,
     * or on 8080 when it is not set.
     */
    public static void start() throws IOException {
        start(resolvePort(System.getenv("PORT")));
    }

    public static void start(int port) throws IOException {
        registerShutdownHook();
        server.start(port);
    }

    // SIGTERM (docker stop, Ctrl+C, systemd) runs JVM shutdown hooks. The hook
    // stops accepting connections and waits for in-flight requests before the
    // JVM is allowed to exit.
    private static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop();
            try {
                server.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "graceful-shutdown"));
    }

    public static void stop() {
        server.stop();
    }
}
