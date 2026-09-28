package co.edu.escuelaing.webframework;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrentRequestTest {

    private static final long HANDLER_DELAY_MS = 1000;
    private static final int PARALLEL_REQUESTS = 5;

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

        server = new HttpServer(router, new StaticFileService(), 8);
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

    @AfterEach
    void stopServer() throws Exception {
        server.stop();
        // Unblock accept() so the server loop can observe the stop flag.
        try (Socket ignored = new Socket("localhost", port)) {
            // connection only used as a wake-up signal
        } catch (IOException ignored) {
            // server already gone
        }
        serverThread.join(5000);
    }

    @Test
    void slowRequestsAreServedInParallel() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/slow")).build();

        long start = System.nanoTime();
        List<CompletableFuture<HttpResponse<String>>> responses = new ArrayList<>();
        for (int i = 0; i < PARALLEL_REQUESTS; i++) {
            responses.add(client.sendAsync(request, HttpResponse.BodyHandlers.ofString()));
        }
        responses.forEach(future -> {
            HttpResponse<String> response = future.join();
            assertEquals(200, response.statusCode());
            assertEquals("done", response.body());
        });
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // A sequential server would need PARALLEL_REQUESTS * HANDLER_DELAY_MS (5 s).
        assertTrue(elapsedMs < 2 * HANDLER_DELAY_MS,
                "Expected parallel handling (< " + 2 * HANDLER_DELAY_MS + " ms) but took " + elapsedMs + " ms");
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
