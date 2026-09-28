package co.edu.escuelaing.webframework;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public final class WebFramework {

    private static final Router router = new Router();
    private static final StaticFileService staticFileService = new StaticFileService();
    private static final HttpServer server = new HttpServer(router, staticFileService, threadsFromEnv());

    private WebFramework() {
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

    public static void start() throws IOException {
        registerShutdownHook();
        server.start();
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
