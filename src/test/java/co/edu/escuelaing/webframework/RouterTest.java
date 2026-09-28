package co.edu.escuelaing.webframework;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RouterTest {

    @Test
    void findsRegisteredRoute() {
        Router router = new Router();
        router.addRoute("GET", "/hello", (req, resp) -> "hi");

        Route route = router.findRoute("GET", "/hello");

        assertNotNull(route);
        assertEquals("hi", route.getHandler().handle(null, null));
    }

    @Test
    void returnsNullForUnregisteredPath() {
        Router router = new Router();
        router.addRoute("GET", "/hello", (req, resp) -> "hi");

        assertNull(router.findRoute("GET", "/unknown"));
    }

    @Test
    void supportsMultipleIndependentRoutes() {
        Router router = new Router();
        router.addRoute("GET", "/hello", (req, resp) -> "hello");
        router.addRoute("GET", "/pi", (req, resp) -> "3.14");

        assertEquals("hello", router.findRoute("GET", "/hello").getHandler().handle(null, null));
        assertEquals("3.14", router.findRoute("GET", "/pi").getHandler().handle(null, null));
    }
}
