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

        String environment = System.getenv().getOrDefault("APP_ENV", "development");
        if (environment.equals("development")) {
            get("/shutdown", (req, resp) -> {
                stop();
                return "Server will stop after this response.";
            });
        }

        String portValue = System.getenv("PORT");
        int port = (portValue == null || portValue.isBlank())
                ? 8080
                : Integer.parseInt(portValue);

        start(port);
    }
}
