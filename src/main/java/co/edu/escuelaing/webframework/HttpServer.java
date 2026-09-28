package co.edu.escuelaing.webframework;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class HttpServer {

    private static final int DEFAULT_PORT = 8080;

    private final Router router;
    private final StaticFileService staticFileService;
    private volatile boolean running = false;

    public HttpServer(Router router, StaticFileService staticFileService) {
        this.router = router;
        this.staticFileService = staticFileService;
    }

    public void start() throws IOException {
        start(DEFAULT_PORT);
    }

    public void start(int port) throws IOException {
        running = true;

        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(new InetSocketAddress(port));
            System.out.println("Server listening on port " + port);

            while (running) {
                try (Socket clientSocket = serverSocket.accept()) {
                    handleConnection(clientSocket);
                } catch (IOException e) {
                    // A single bad connection must not bring the server down.
                    System.err.println("Error handling connection: " + e.getMessage());
                }
            }
        }

        System.out.println("Server stopped gracefully.");
    }

    public void stop() {
        running = false;
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
