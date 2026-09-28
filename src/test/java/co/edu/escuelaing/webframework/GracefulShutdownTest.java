package co.edu.escuelaing.webframework;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GracefulShutdownTest {

    private static final long HANDLER_DELAY_MS = 1000;

    private HttpServer server;
    private Thread serverThread;
    private int port;

    @BeforeEach
    void startServer() throws Exception {
        Router router = new Router();
        router.addRoute("GET", "/slow", (req, resp) -> {
            try {
                Thread.sleep(HANDLER_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "done";
        });
        router.addRoute("GET", "/fast", (req, resp) -> "fast");

        server = new HttpServer(router, new StaticFileService(), 4);
        port = freePort();
        serverThread = new Thread(() -> {
            try {
                server.start(port);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        serverThread.start();
        waitUntilListening(port);
    }

    @Test
    void stopReturnsWithoutNeedingAnotherConnection() throws Exception {
        server.stop();

        assertTrue(server.awaitTermination(3, TimeUnit.SECONDS), "server did not stop");
        serverThread.join(1000);
        assertFalse(serverThread.isAlive());
        assertFalse(server.isRunning());
    }

    @Test
    void inFlightRequestCompletesAndNewConnectionsAreRefused() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        CompletableFuture<HttpResponse<String>> inFlight = client.sendAsync(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/slow")).build(),
                HttpResponse.BodyHandlers.ofString());

        // Let the slow request reach its handler before shutting down.
        Thread.sleep(300);
        server.stop();

        assertThrows(IOException.class, () -> new Socket("localhost", port).close(),
                "new connections must be refused once shutdown starts");

        HttpResponse<String> response = inFlight.get(5, TimeUnit.SECONDS);
        assertEquals(200, response.statusCode());
        assertEquals("done", response.body());

        assertTrue(server.awaitTermination(5, TimeUnit.SECONDS), "server did not finish draining");
    }

    @Test
    void stopIsIdempotent() throws Exception {
        server.stop();
        server.stop();

        assertTrue(server.awaitTermination(3, TimeUnit.SECONDS));
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void waitUntilListening(int port) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            try (Socket ignored = new Socket("localhost", port)) {
                return;
            } catch (IOException e) {
                Thread.sleep(100);
            }
        }
        throw new IllegalStateException("Server did not start on port " + port);
    }
}
