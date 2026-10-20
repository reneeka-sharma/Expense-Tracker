package com.expense.tracker;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;


public class ApiServer {
    private final HttpServer server;

    public ApiServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newCachedThreadPool());
    }

    public void start() {
        server.start();
    }

    private void handle(HttpExchange ex) throws IOException {
        try {
            addCors(ex);

            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();

            if (method.equals("OPTIONS")) {
                send(ex, 204, "");
                return;
            }

            // Frontend
            if (path.equals("/") || path.equals("/index.html")) {
                serve(ex, "index.html", "text/html");
                return;
            }
            if (path.equals("/style.css")) {
                serve(ex, "style.css", "text/css");
                return;
            }
            if (path.equals("/app.js")) {
                serve(ex, "app.js", "application/javascript");
                return;
            }

            // API
            if (path.equals("/api/login") && method.equals("POST")) {
                Map<String,String> b = body(ex);
                login(ex, b);
                return;
            }

            if (path.equals("/api/summary") && method.equals("GET")) {
                summary(ex, Integer.parseInt(query(ex, "userId")));
                return;
            }

            if (path.equals("/api/transactions") && method.equals("GET")) {
                transactions(ex, Integer.parseInt(query(ex, "userId")));
                return;
            }

            if (path.equals("/api/categories") && method.equals("GET")) {
                categories(ex, query(ex, "type"));
                return;
            }

            if (path.equals("/api/transactions") && method.equals("POST")) {
                addTransaction(ex, body(ex));
                return;
            }

            if (path.startsWith("/api/transactions/") && method.equals("DELETE")) {
                deleteTransaction(
                    ex,
                    Integer.parseInt(path.substring("/api/transactions/".length())),
                    Integer.parseInt(query(ex, "userId"))
                );
                return;
            }

            if (path.startsWith("/api/transactions/") && method.equals("PUT")) {
                updateTransaction(
                    ex,
                    Integer.parseInt(path.substring("/api/transactions/".length())),
                    body(ex)
            );
                return;
           }

            sendJson(ex, 404, "{\"error\":\"Endpoint not found\"}");

        } catch (Exception e) {
            e.printStackTrace();
            sendJson(ex, 500, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
        }
    }

    private void login(HttpExchange ex, Map<String,String> b) throws Exception {
        String sql = "SELECT user_id,name,email FROM users WHERE email=? AND password=?";

        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, b.get("email"));
            ps.setString(2, b.get("password"));

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String json = "{"
                        + "\"userId\":" + rs.getInt("user_id") + ","
                        + "\"name\":\"" + escape(rs.getString("name")) + "\","
                        + "\"email\":\"" + escape(rs.getString("email")) + "\""
                        + "}";
                    sendJson(ex, 200, json);
                    return;
                }
            }
        }

        sendJson(ex, 401, "{\"error\":\"Invalid email or password\"}");
    }

    private void summary(HttpExchange ex, int userId) throws Exception {
        double income = total(userId, "INCOME");
        double expense = total(userId, "EXPENSE");
        double balance = income - expense;

        StringBuilder json = new StringBuilder();
        json.append("{")
            .append("\"income\":").append(income).append(",")
            .append("\"expense\":").append(expense).append(",")
            .append("\"balance\":").append(balance).append(",")
            .append("\"byCategory\":[");

        String sql = """
            SELECT c.name, c.icon, SUM(t.amount) AS total
            FROM transactions t
            JOIN categories c ON t.category_id = c.category_id
            WHERE t.user_id=? AND t.transaction_type='EXPENSE'
            GROUP BY c.category_id,c.name,c.icon
            ORDER BY total DESC
            """;

        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    first = false;
                    json.append("{")
                        .append("\"name\":\"").append(escape(rs.getString("name"))).append("\",")
                        .append("\"icon\":\"").append(escape(rs.getString("icon"))).append("\",")
                        .append("\"total\":").append(rs.getDouble("total"))
                        .append("}");
                }
            }
        }

        json.append("]}");
        sendJson(ex, 200, json.toString());
    }

    private double total(int userId, String type) throws SQLException {
        String sql = "SELECT COALESCE(SUM(amount),0) FROM transactions WHERE user_id=? AND transaction_type=?";
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, type);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getDouble(1);
            }
        }
    }

    private void transactions(HttpExchange ex, int userId) throws Exception {
        
String sql = """
    SELECT t.transaction_id,t.amount,t.transaction_type,t.description,
           t.transaction_date,t.category_id,c.name category,c.icon
    FROM transactions t
    JOIN categories c ON t.category_id=c.category_id
    WHERE t.user_id=?
    ORDER BY t.transaction_date DESC,t.transaction_id DESC
    """;


        StringBuilder json = new StringBuilder("[");
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    first = false;

                    json.append("{")
                        .append("\"id\":").append(rs.getInt("transaction_id")).append(",")
                        .append("\"amount\":").append(rs.getDouble("amount")).append(",")
                        .append("\"type\":\"").append(rs.getString("transaction_type")).append("\",")
                        .append("\"description\":\"").append(escape(rs.getString("description"))).append("\",")
                        .append("\"date\":\"").append(rs.getDate("transaction_date")).append("\",")
                        .append("\"category\":\"").append(escape(rs.getString("category"))).append("\",")
                        .append("\"categoryId\":").append(rs.getInt("category_id")).append(",")
                        .append("\"icon\":\"").append(escape(rs.getString("icon"))).append("\"")
                        .append("}");
                }
            }
        }
        json.append("]");
        sendJson(ex, 200, json.toString());
    }

    private void categories(HttpExchange ex, String type) throws Exception {
        String sql = "SELECT category_id,name,icon FROM categories WHERE type=? ORDER BY name";
        StringBuilder json = new StringBuilder("[");
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, type);
            try (ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    first = false;
                    json.append("{")
                        .append("\"id\":").append(rs.getInt("category_id")).append(",")
                        .append("\"name\":\"").append(escape(rs.getString("name"))).append("\",")
                        .append("\"icon\":\"").append(escape(rs.getString("icon"))).append("\"")
                        .append("}");
                }
            }
        }
        json.append("]");
        sendJson(ex, 200, json.toString());
    }

    private void addTransaction(HttpExchange ex, Map<String,String> b) throws Exception {
        int userId = Integer.parseInt(b.get("userId"));
        int categoryId = Integer.parseInt(b.get("categoryId"));
        double amount = Double.parseDouble(b.get("amount"));
        String type = b.get("type");
        String description = b.getOrDefault("description", "");
        LocalDate date = LocalDate.parse(b.get("date"));

        if (amount <= 0 || (!type.equals("INCOME") && !type.equals("EXPENSE"))) {
            sendJson(ex, 400, "{\"error\":\"Invalid transaction\"}");
            return;
        }

        // OOP: actual object is either Expense or Income.
        Transaction transaction = type.equals("EXPENSE")
            ? new Expense(userId, categoryId, amount, description, date)
            : new Income(userId, categoryId, amount, description, date);

        String sql = """
            INSERT INTO transactions
            (user_id,category_id,amount,transaction_type,description,transaction_date)
            VALUES (?,?,?,?,?,?)
            """;

        try (Connection c = DB.getConnection()) {
            try {
                c.setAutoCommit(false);

                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setInt(1, transaction.getUserId());
                    ps.setInt(2, transaction.getCategoryId());
                    ps.setDouble(3, transaction.getAmount());
                    ps.setString(4, transaction.getType());
                    ps.setString(5, transaction.getDescription());
                    ps.setDate(6, Date.valueOf(transaction.getDate()));
                    ps.executeUpdate();
                }

                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }

        sendJson(ex, 201, "{\"message\":\"Transaction added\"}");
    }

    private void deleteTransaction(HttpExchange ex, int id, int userId) throws Exception {
        String sql = "DELETE FROM transactions WHERE transaction_id=? AND user_id=?";
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
        sendJson(ex, 200, "{\"message\":\"Transaction deleted\"}");
    }


private void updateTransaction(HttpExchange ex, int id, Map<String, String> b)
        throws Exception {

    int userId = Integer.parseInt(b.get("userId"));
    int categoryId = Integer.parseInt(b.get("categoryId"));
    double amount = Double.parseDouble(b.get("amount"));
    String type = b.get("type");
    String description = b.getOrDefault("description", "");
    LocalDate date = LocalDate.parse(b.get("date"));

    if (amount <= 0 ||
        (!type.equals("INCOME") && !type.equals("EXPENSE"))) {
        sendJson(ex, 400, "{\"error\":\"Invalid transaction\"}");
        return;
    }

    // Verify the category matches the transaction type.
    String categorySql = "SELECT category_id FROM categories WHERE category_id=? AND type=?";

    try (Connection c = DB.getConnection()) {
        try (PreparedStatement check = c.prepareStatement(categorySql)) {
            check.setInt(1, categoryId);
            check.setString(2, type);

            try (ResultSet rs = check.executeQuery()) {
                if (!rs.next()) {
                    sendJson(ex, 400, "{\"error\":\"Invalid category for transaction type\"}");
                    return;
                }
            }
        }

        String sql = """
            UPDATE transactions
            SET category_id=?, amount=?, transaction_type=?,
                description=?, transaction_date=?
            WHERE transaction_id=? AND user_id=?
            """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, categoryId);
            ps.setDouble(2, amount);
            ps.setString(3, type);
            ps.setString(4, description);
            ps.setDate(5, java.sql.Date.valueOf(date));
            ps.setInt(6, id);
            ps.setInt(7, userId);

            int updated = ps.executeUpdate();

            if (updated == 0) {
                sendJson(ex, 404, "{\"error\":\"Transaction not found\"}");
                return;
            }
        }
    }

    sendJson(ex, 200, "{\"message\":\"Transaction updated successfully\"}");
}


    private Map<String,String> body(HttpExchange ex) throws IOException {
        String json = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String,String> map = new HashMap<>();

        // This parser is intentionally simple for this college project.
        String inner = json.trim();
        if (inner.startsWith("{")) inner = inner.substring(1, inner.length()-1);

        for (String part : inner.split(",(?=\")")) {
            String[] pair = part.split(":", 2);
            if (pair.length == 2) {
                String key = clean(pair[0]);
                String value = clean(pair[1]);
                map.put(key, value);
            }
        }
        return map;
    }

    private String clean(String s) {
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length()-1);
        }
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private String query(HttpExchange ex, String key) {
        String q = ex.getRequestURI().getRawQuery();
        if (q == null) return "";
        for (String part : q.split("&")) {
            String[] p = part.split("=",2);
            if (p.length == 2 && p[0].equals(key)) {
                return URLDecoder.decode(p[1], StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    private void serve(HttpExchange ex, String file, String contentType) throws IOException {
        Path path = Paths.get("src/main/resources/static", file);
        if (!Files.exists(path)) {
            path = Paths.get("target/classes/static", file);
        }
        byte[] data = Files.readAllBytes(path);
        ex.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
        send(ex, 200, data);
    }

    private void addCors(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private void sendJson(HttpExchange ex, int code, String json) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        send(ex, code, json.getBytes(StandardCharsets.UTF_8));
    }

    private void send(HttpExchange ex, int code, String text) throws IOException {
        send(ex, code, text.getBytes(StandardCharsets.UTF_8));
    }

    private void send(HttpExchange ex, int code, byte[] data) throws IOException {
        ex.sendResponseHeaders(code, data.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(data);
        }
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\","\\\\").replace("\"","\\\"")
                .replace("\n"," ").replace("\r"," ");
    }
}
