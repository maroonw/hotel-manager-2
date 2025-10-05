package db;

import java.sql.*;
import java.nio.file.*;
import java.util.*;
import java.io.*;



public final class Database {

    static String password = "Bottle33!";
    static String user = "root";
    static String port = "3306";

    private Database() {
    }

    // Setup connection
    public static Connection connect() throws SQLException {
        // SET PROPERTIES!
        String url =
                "jdbc:mysql://127.0.0.1:" + port + "/HOTEL_MANAGEMENT"
                        + "?useSSL=false"
                        + "&allowPublicKeyRetrieval=true"
                        + "&serverTimezone=UTC"
                        + "&connectTimeout=5000"
                        + "&socketTimeout=10000";
        // String user = "root";
        // String password = "root";
        return DriverManager.getConnection(url, user, password);
    }



    // Run .sql file statements
    public static void runSqlFile(Connection con, Path path) throws IOException,SQLException {
        String sql = Files.readString(path);

        for (String stmt : sql.split(";")) {
            String s = stmt.trim();
            if (s.isEmpty()) continue;
            try (Statement st = con.createStatement()) {
                st.execute(s);
            }
        }
    }

    // run sql resources in /resources/sql folder insert and create
    // we use this mainly for populating the data using the queries we started with
    public static void runSqlResource(Connection con, String resourcePath) throws Exception {
        try (var in = Database.class.getResourceAsStream(resourcePath)) {
            if (in == null) throw new FileNotFoundException("Classpath resource not found: " + resourcePath);

            String raw = new String(in.readAllBytes());

            // Simple splitter that respects ; as statement terminator and skips -- comments
            StringBuilder stmt = new StringBuilder();
            try (Statement st = con.createStatement()) {
                for (String line : raw.split("\n")) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("--") || trimmed.isEmpty()) continue; // skip comments/blank lines
                    stmt.append(line).append("\n");
                    if (trimmed.endsWith(";")) {
                        String sql = stmt.toString().trim();
                        sql = sql.substring(0, sql.length() - 1); // drop trailing ;
                        String preview = sql.replaceAll("\\s+", " ");
                        if (preview.length() > 120) preview = preview.substring(0, 120) + "...";
                        System.out.println(">> SQL: " + preview);
                        try {
                            boolean hasRs = st.execute(sql);
                            if (hasRs) {
                                try (var rs = st.getResultSet()) {
                                    // drain results if SELECT present; no output needed here
                                    while (rs.next()) { /* no-op */ }
                                }
                            }
                        } catch (SQLException ex) {
                            System.err.println("!! FAILED on statement:\n" + sql);
                            throw ex; // bubble up so your menu shows the real cause
                        }
                        stmt.setLength(0);
                    }
                }
            }
        }
    }


    // Only needed if you want to execute SELECT statements from a script:
    // you can select through java or through the select query, this is the
    // functionallity for running it through the scripts we started with
    public static void runSqlResourceSelects(Connection con, String resourcePath) throws Exception {
        try (var in = Database.class.getResourceAsStream(resourcePath)) {
            if (in == null) throw new FileNotFoundException("Classpath resource not found: " + resourcePath);
            String sql = new String(in.readAllBytes());
            for (String stmt : sql.split(";")) {
                String s = stmt.trim();
                if (s.isEmpty() || s.startsWith("--")) continue;
                try (Statement st = con.createStatement()) {
                    boolean hasResult = st.execute(s);
                    if (hasResult) {
                        try (var rs = st.getResultSet()) {
                            int cols = rs.getMetaData().getColumnCount();
                            while (rs.next()) {
                                // simple one-line dump per row (optional)
                                StringBuilder line = new StringBuilder();
                                for (int i = 1; i <= cols; i++) {
                                    if (i > 1) line.append(" | ");
                                    line.append(rs.getMetaData().getColumnLabel(i)).append("=")
                                            .append(String.valueOf(rs.getObject(i)));
                                }
                                System.out.println(line);
                            }
                        }
                    }
                }
            }
        }
    }

    // if schema exists already
    // needed for populating data, eliminates the need to delete everything
    public static Connection connectAppDb() throws SQLException {
        return DriverManager.getConnection(
                "jdbc:mysql://127.0.0.1:"+ port + "/HOTEL_MANAGEMENT?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                user, password);
    }

    // if schema doesn't exist
    public static Connection connectServer() throws SQLException {
        return DriverManager.getConnection(
                "jdbc:mysql://127.0.0.1:" + port + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                user, password);
    }

}
