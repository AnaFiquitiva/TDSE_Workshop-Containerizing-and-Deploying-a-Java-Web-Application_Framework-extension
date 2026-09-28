package co.edu.escuelaing.webframework;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

public class HttpServer {

    private static final int DEFAULT_PORT = 8080;
    private static final int DEFAULT_THREADS = 16;
    // Kept below Docker's default 10 s stop grace period so in-flight requests
    // are drained before the container runtime escalates to SIGKILL.
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 8;

    private final Router router;
    private final StaticFileService staticFileService;
    private final int threads;
    private final CountDownLatch terminated = new CountDownLatch(1);
    private volatile boolean running = false;
    private volatile ServerSocket serverSocket;
    private ExecutorService workers;

    public HttpServer(Router router, StaticFileService staticFileService) {
        this(router, staticFileService, DEFAULT_THREADS);
    }

    public HttpServer(Router router, StaticFileService staticFileService, int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException("Thread pool size must be at least 1, got " + threads);
        }
        this.router = router;
        this.staticFileService = staticFileService;
        this.threads = threads;
    }

    public void start() throws IOException {
        start(DEFAULT_PORT);
    }

    public void start(int port) throws IOException {
        workers = Executors.newFixedThreadPool(threads);

        try {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress(port));
            running = true;
            System.out.println("Server listening on port " + port + " with " + threads + " worker threads");

            while (running) {
                Socket clientSocket;
                try {
                    clientSocket = serverSocket.accept();
                } catch (IOException e) {
                    if (!running) {
                        // stop() closed the listening socket to unblock accept().
                        break;
                    }
                    // A single failed accept must not bring the server down.
                    System.err.println("Error accepting connection: " + e.getMessage());
                    continue;
                }

                // The accept loop only hands the connection off; a worker thread
                // parses, dispatches and answers it, so a slow handler no longer
                // blocks every other client.
                try {
                    workers.submit(() -> serve(clientSocket));
                } catch (RejectedExecutionException e) {
                    closeQuietly(clientSocket);
                }
            }
        } finally {
            running = false;
            closeListener();
            drainWorkers();
            terminated.countDown();
        }

        System.out.println("Server stopped gracefully.");
    }

    /**
     * Requests a graceful shutdown: new connections are refused immediately,
     * while requests already being handled are allowed to finish. Safe to call
     * more than once and from any thread, including a request handler.
     */
    public void stop() {
        if (!running) {
            return;
        }
        System.out.println("Shutdown requested: no longer accepting new connections.");
        running = false;
        closeListener();
    }

    /**
     * Blocks until the server has stopped and in-flight requests were drained,
     * or until the timeout elapses.
     */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return terminated.await(timeout, unit);
    }

    public boolean isRunning() {
        return running;
    }

    private void closeListener() {
        ServerSocket socket = serverSocket;
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Error closing server socket: " + e.getMessage());
            }
        }
    }

    private void drainWorkers() {
        workers.shutdown();
        try {
            if (!workers.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                System.err.println("In-flight requests did not finish within "
                        + SHUTDOWN_TIMEOUT_SECONDS + " s; forcing shutdown.");
                workers.shutdownNow();
            } else {
                System.out.println("All in-flight requests completed.");
            }
        } catch (InterruptedException e) {
            workers.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void serve(Socket clientSocket) {
        try (clientSocket) {
            handleConnection(clientSocket);
        } catch (IOException e) {
            System.err.println("Error handling connection: " + e.getMessage());
        }
    }

    private void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Nothing useful to do if the socket cannot be closed.
        }
    }

    private void handleConnection(Socket clientSocket) {
        try (
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.ISO_8859_1));
                OutputStream out = clientSocket.getOutputStream()
        ) {
            String requestLine = in.readLine();

            if (requestLine == null || requestLine.isBlank()) {
                // Empty or incomplete request line: nothing to do, close quietly.
                return;
            }

            consumeHeaders(in);

            Request request;
            try {
                request = Request.parse(requestLine);
            } catch (IllegalArgumentException e) {
                sendResponse(out, 400, "Bad Request", "text/plain; charset=utf-8",
                        "400 Bad Request".getBytes(StandardCharsets.UTF_8));
                return;
            }

            dispatch(request, out);

        } catch (IOException e) {
            System.err.println("I/O error while handling client: " + e.getMessage());
        }
    }

    private void consumeHeaders(BufferedReader in) throws IOException {
        String header;
        while ((header = in.readLine()) != null && !header.isBlank()) {
            // Headers are not needed by this lab; they are only drained so the
            // connection is left in a clean state.
        }
    }

    private void dispatch(Request request, OutputStream out) throws IOException {
        Route route = router.findRoute(request.getMethod(), request.getPath());

        if (route != null) {
            runDynamicRoute(route, request, out);
            return;
        }

        serveStaticResource(request, out);
    }

    private void runDynamicRoute(Route route, Request request, OutputStream out) throws IOException {
        Response response = new Response();
        String body;
        try {
            body = route.getHandler().handle(request, response);
        } catch (Exception e) {
            System.err.println("Error executing handler for " + request.getPath() + ": " + e.getMessage());
            sendResponse(out, 500, "Internal Server Error", "text/plain; charset=utf-8",
                    "500 Internal Server Error".getBytes(StandardCharsets.UTF_8));
            return;
        }

        if (body == null) {
            body = "";
        }

        sendResponse(out, response.getStatusCode(), response.getStatusText(),
                response.getContentType(), body.getBytes(StandardCharsets.UTF_8));
    }

    private void serveStaticResource(Request request, OutputStream out) throws IOException {
        String path = request.getPath().equals("/") ? "/index.html" : request.getPath();

        byte[] content;
        try {
            content = staticFileService.readResource(path);
        } catch (IOException e) {
            System.err.println("Error reading static resource " + path + ": " + e.getMessage());
            content = null;
        }

        if (content == null) {
            sendResponse(out, 404, "Not Found", "text/plain; charset=utf-8",
                    "404 Not Found".getBytes(StandardCharsets.UTF_8));
            return;
        }

        sendResponse(out, 200, "OK", staticFileService.getContentType(path), content);
    }

    private void sendResponse(OutputStream out, int statusCode, String statusText,
                               String contentType, byte[] body) throws IOException {
        StringBuilder headers = new StringBuilder();
        headers.append("HTTP/1.1 ").append(statusCode).append(' ').append(statusText).append("\r\n");
        headers.append("Content-Type: ").append(contentType).append("\r\n");
        headers.append("Content-Length: ").append(body.length).append("\r\n");
        headers.append("Connection: close\r\n");
        headers.append("\r\n");

        out.write(headers.toString().getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }
}
