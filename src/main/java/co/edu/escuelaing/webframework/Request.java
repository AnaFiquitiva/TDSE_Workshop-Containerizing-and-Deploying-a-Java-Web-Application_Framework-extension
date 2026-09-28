package co.edu.escuelaing.webframework;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class Request {

    private final String method;
    private final String path;
    private final Map<String, String> queryParams;

    public Request(String method, String path, Map<String, String> queryParams) {
        this.method = method;
        this.path = path;
        this.queryParams = queryParams;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getValue(String name) {
        return queryParams.get(name);
    }

    public Map<String, String> getValues() {
        return queryParams;
    }

    public static Request parse(String requestLine) {
        if (requestLine == null || requestLine.isBlank()) {
            throw new IllegalArgumentException("Empty request line");
        }

        String[] parts = requestLine.trim().split("\\s+");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Malformed request line: " + requestLine);
        }

        String method = parts[0];
        String fullPath = parts[1];

        String path;
        Map<String, String> queryParams;

        int queryIndex = fullPath.indexOf('?');
        if (queryIndex >= 0) {
            path = fullPath.substring(0, queryIndex);
            queryParams = parseQuery(fullPath.substring(queryIndex + 1));
        } else {
            path = fullPath;
            queryParams = new HashMap<>();
        }

        if (path.isEmpty()) {
            path = "/";
        }

        return new Request(method, path, queryParams);
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isBlank()) {
            return params;
        }

        for (String pair : query.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            int eq = pair.indexOf('=');
            try {
                if (eq >= 0) {
                    String key = decode(pair.substring(0, eq));
                    String value = decode(pair.substring(eq + 1));
                    params.put(key, value);
                } else {
                    params.put(decode(pair), "");
                }
            } catch (RuntimeException e) {
                // Ignore a malformed query parameter instead of failing the whole request.
            }
        }
        return params;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }
}
