package co.edu.escuelaing.webframework;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Router {

    // Routes are registered once at startup and then read by many worker
    // threads concurrently, which is the access pattern CopyOnWriteArrayList
    // is designed for.
    private final List<Route> routes = new CopyOnWriteArrayList<>();

    public void addRoute(String method, String path, Route.Service handler) {
        routes.add(new Route(method, path, handler));
    }

    public Route findRoute(String method, String path) {
        for (Route route : routes) {
            if (route.matches(method, path)) {
                return route;
            }
        }
        return null;
    }
}
