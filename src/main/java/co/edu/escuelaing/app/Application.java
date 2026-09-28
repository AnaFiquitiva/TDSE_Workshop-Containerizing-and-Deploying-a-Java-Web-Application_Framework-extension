package co.edu.escuelaing.app;

import static co.edu.escuelaing.webframework.WebFramework.get;
import static co.edu.escuelaing.webframework.WebFramework.staticfiles;
import static co.edu.escuelaing.webframework.WebFramework.start;
import static co.edu.escuelaing.webframework.WebFramework.stop;

public class Application {

    public static void main(String[] args) throws Exception {
        String staticFilesPath = System.getenv().getOrDefault("STATIC_FILES_PATH", "/webroot");
        staticfiles(staticFilesPath);

        get("/hello", (req, resp) -> {
            String name = req.getValue("name");
            if (name == null || name.isBlank()) {
                name = "world";
            }

            String greetingPrefix = System.getenv().getOrDefault("GREETING_PREFIX", "Hello");
            return greetingPrefix + " " + name;
        });

        get("/pi", (req, resp) -> String.valueOf(Math.PI));

        // Simulates a slow handler so concurrent request handling can be observed:
        // several /slow calls in parallel finish in about one delay, not the sum.
        get("/slow", (req, resp) -> {
            long millis = parseDelay(req.getValue("ms"));
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "Done after " + millis + " ms on " + Thread.currentThread().getName();
        });

        String environment = System.getenv().getOrDefault("APP_ENV", "development");
        if (environment.equals("development")) {
            get("/shutdown", (req, resp) -> {
                stop();
                return "Server will stop after this response.";
            });
        }

        // The framework reads the listening port from the PORT environment variable.
        start();
    }

    private static long parseDelay(String value) {
        if (value == null || value.isBlank()) {
            return 2000;
        }
        try {
            return Math.max(0, Math.min(10_000, Long.parseLong(value.trim())));
        } catch (NumberFormatException e) {
            return 2000;
        }
    }
}
