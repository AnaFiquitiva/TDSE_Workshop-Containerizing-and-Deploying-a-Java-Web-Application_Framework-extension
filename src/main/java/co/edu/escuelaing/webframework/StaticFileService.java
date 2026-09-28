package co.edu.escuelaing.webframework;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class StaticFileService {

    private String staticFilesRoot = "/webroot";

    public void setStaticFilesRoot(String root) {
        this.staticFilesRoot = normalizeRoot(root);
    }

    private String normalizeRoot(String root) {
        if (root == null || root.isBlank()) {
            return "/webroot";
        }
        return root.startsWith("/") ? root : "/" + root;
    }

    public byte[] readResource(String path) throws IOException {
        String resourcePath = buildResourcePath(path);
        try (InputStream in = StaticFileService.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            in.transferTo(out);
            return out.toByteArray();
        }
    }

    private String buildResourcePath(String path) {
        String cleanPath = (path == null || path.isBlank()) ? "/" : path;
        if (!cleanPath.startsWith("/")) {
            cleanPath = "/" + cleanPath;
        }
        return staticFilesRoot + cleanPath;
    }

    public String getContentType(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=utf-8";
        if (lower.endsWith(".css")) return "text/css; charset=utf-8";
        if (lower.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (lower.endsWith(".json")) return "application/json; charset=utf-8";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".ico")) return "image/x-icon";
        if (lower.endsWith(".txt")) return "text/plain; charset=utf-8";
        return "application/octet-stream";
    }
}
