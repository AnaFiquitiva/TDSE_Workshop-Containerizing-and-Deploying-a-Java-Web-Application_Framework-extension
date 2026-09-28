package co.edu.escuelaing.webframework;

public class Route {

    @FunctionalInterface
    public interface Service {
        String handle(Request req, Response resp);
    }

    private final String method;
    private final String path;
    private final Service handler;

    public Route(String method, String path, Service handler) {
        this.method = method;
        this.path = path;
        this.handler = handler;
    }

    public boolean matches(String method, String path) {
        return this.method.equalsIgnoreCase(method) && this.path.equals(path);
    }

    public Service getHandler() {
        return handler;
    }

    public String getPath() {
        return path;
    }

    public String getMethod() {
        return method;
    }
}
