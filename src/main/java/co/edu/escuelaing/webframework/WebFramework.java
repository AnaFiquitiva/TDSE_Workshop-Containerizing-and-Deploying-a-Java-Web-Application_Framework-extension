package co.edu.escuelaing.webframework;

import java.io.IOException;

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
        server.start();
    }

    public static void start(int port) throws IOException {
        server.start(port);
    }

    public static void stop() {
        server.stop();
    }
}
