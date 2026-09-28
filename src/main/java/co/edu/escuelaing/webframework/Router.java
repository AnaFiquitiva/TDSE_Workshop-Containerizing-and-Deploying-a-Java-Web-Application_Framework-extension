package co.edu.escuelaing.webframework;

import java.util.ArrayList;
import java.util.List;

public class Router {

    private final List<Route> routes = new ArrayList<>();

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
