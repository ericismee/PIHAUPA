package piHAUPA;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public final class WebServer {
    private static final Path WEB_ROOT = Path.of("web").toAbsolutePath().normalize();
    private static final int MAX_INPUT_BYTES = 20 * 1024 * 1024;

    private WebServer() {
    }

    public static void main(String[] args) throws Exception {
        int requestedPort = args.length == 0 ? 8000 : Integer.parseInt(args[0]);
        HttpServer server = bind(requestedPort);
        server.createContext("/api/analyze", WebServer::analyze);
        server.createContext("/api/export", WebServer::export);
        server.createContext("/", WebServer::serveStatic);
        server.setExecutor(Executors.newFixedThreadPool(Math.max(2,
                Runtime.getRuntime().availableProcessors())));
        server.start();
        int port = server.getAddress().getPort();
        URI uri = URI.create("http://localhost:" + port + "/");
        System.out.println("PIHAUPA Web dang chay tai " + uri);
        System.out.println("Nhan Ctrl+C de dung.");
        boolean openBrowser = args.length < 2 || !"--no-browser".equalsIgnoreCase(args[1]);
        if (openBrowser && Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(uri);
        }
    }

    private static HttpServer bind(int requestedPort) throws IOException {
        try {
            return HttpServer.create(new InetSocketAddress("127.0.0.1", requestedPort), 0);
        } catch (IOException error) {
            throw new IOException("Cổng " + requestedPort + " đang được sử dụng. "
                    + "Hãy đóng server PIHAUPA cũ hoặc chọn cổng khác khi chạy run-web.ps1.", error);
        }
    }

    private static void analyze(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "application/json", "{\"error\":\"Chỉ hỗ trợ POST.\"}");
            return;
        }
        try {
            InputData input = parseInput(exchange);
            ComparisonReport report = ComparisonReport.run(input);
            send(exchange, 200, "application/json; charset=utf-8", report.toJson());
        } catch (Exception error) {
            sendError(exchange, error);
        }
    }

    private static void export(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "application/json; charset=utf-8", "{\"error\":\"Chỉ hỗ trợ POST.\"}");
            return;
        }
        try {
            InputData input = parseInput(exchange);
            ComparisonReport report = ComparisonReport.run(input);
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            String source = query.getOrDefault("source", "Dữ liệu web");
            String format = query.getOrDefault("format", "txt");
            if (!format.equals("txt") && !format.equals("csv")) {
                throw new IllegalArgumentException("Chỉ hỗ trợ xuất TXT hoặc CSV.");
            }
            byte[] body = (format.equals("txt") ? ExportBundle.reportText(input, report, source)
                    : ExportBundle.reportCsv(input, report, source)).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type",
                    format.equals("txt") ? "text/plain; charset=utf-8" : "text/csv; charset=utf-8");
            exchange.getResponseHeaders().set("Content-Disposition",
                    "attachment; filename=PIHAUPA-bao-cao." + format);
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        } catch (Exception error) {
            sendError(exchange, error);
        }
    }

    private static InputData parseInput(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(MAX_INPUT_BYTES + 1);
        if (bytes.length > MAX_INPUT_BYTES) {
            throw new IllegalArgumentException("File vượt quá giới hạn 20 MB.");
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        double su = Double.parseDouble(query.getOrDefault("su", "0.23"));
        double sl = Double.parseDouble(query.getOrDefault("sl", "0.10"));
        if (!(sl > 0.0 && sl < su && su <= 1.0)) {
            throw new IllegalArgumentException("Cần 0 < Sl < Su ≤ 1.");
        }
        InputData parsed = text.isBlank() ? InputParser.parseBuiltInSample() : InputParser.parseText(text);
        return new InputData(su, sl, parsed.externalUtilities(), parsed.batches());
    }

    private static void sendError(HttpExchange exchange, Exception error) throws IOException {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        send(exchange, 400, "application/json; charset=utf-8",
                "{\"error\":\"" + escape(message) + "\"}");
    }

    private static void serveStatic(HttpExchange exchange) throws IOException {
        String requestPath = exchange.getRequestURI().getPath();
        String relative = "/".equals(requestPath) ? "index.html" : requestPath.substring(1);
        if (!relative.equals("index.html") && !relative.equals("app.js") && !relative.equals("styles.css")) {
            send(exchange, 404, "text/plain; charset=utf-8", "Not found");
            return;
        }
        Path file = WEB_ROOT.resolve(relative).normalize();
        if (!file.startsWith(WEB_ROOT) || !Files.isRegularFile(file)) {
            send(exchange, 404, "text/plain; charset=utf-8", "Not found");
            return;
        }
        String contentType = relative.endsWith(".css") ? "text/css; charset=utf-8"
                : relative.endsWith(".js") ? "text/javascript; charset=utf-8"
                : "text/html; charset=utf-8";
        byte[] body = Files.readAllBytes(file);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> result = new HashMap<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String token : raw.split("&")) {
            String[] pair = token.split("=", 2);
            result.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                    pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "");
        }
        return result;
    }

    private static void send(HttpExchange exchange, int status, String contentType, String text)
            throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
