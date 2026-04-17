package com.example;

import java.sql.*;

public class RawJdbcExperiment {

    // H2 인메모리 DB 설정 — 파일로 유지하려면 jdbc:h2:file:./testdb 로 변경
    private static final String URL = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1";
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    public static void main(String[] args) throws SQLException {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            setupTables(conn);

            System.out.println("=== Case 1: autocommit=true (기본값) ===");
            try {
                Statement stmt1 = conn.createStatement();
                Statement stmt2 = conn.createStatement();
                conn.setAutoCommit(true);
                stmt1.execute("INSERT INTO orders(user_id, amount) VALUES (1, 1)");
                throw new RuntimeException("예외 시작");
            } catch (RuntimeException e) {
                printTableState(conn);
            }

            System.out.println("=== Case 2: autocommit=false, rollback 없음 ===");
            try {
                Statement stmt1 = conn.createStatement();
                Statement stmt2 = conn.createStatement();
                conn.setAutoCommit(false);
                stmt1.execute("INSERT INTO orders(user_id, amount) VALUES (1, 1)");
                throw new RuntimeException("예외 시작");
            } catch (RuntimeException e) {
                printTableState(conn);
            }

            System.out.println("=== Case 3: autocommit=false + rollback() ===");
            try {
                Statement stmt1 = conn.createStatement();
                Statement stmt2 = conn.createStatement();
                conn.setAutoCommit(false);
                stmt1.execute("INSERT INTO orders(user_id, amount) VALUES (1, 1)");
                throw new RuntimeException("예외 시작");
            } catch (RuntimeException e) {
                conn.rollback();
                printTableState(conn);
            }
        }

        System.out.println("=============================================");
        try (Connection conn2 = DriverManager.getConnection(URL, USER, PASSWORD)) {
            printTableState(conn2);
        }
    }

    private static void setupTables(Connection conn) throws SQLException {
        conn.setAutoCommit(false);
        Statement stmt1 = conn.createStatement();
        stmt1.execute("CREATE TABLE orders(" +
                "   id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "   user_id BIGINT," +
                "   amount BIGINT" +
                ")");
        Statement stmt2 = conn.createStatement();
        stmt2.execute("CREATE TABLE point_history (" +
                "   id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "   user_id BIGINT," +
                "   delta BIGINT" +
                ")");
        stmt1.close();
        stmt2.close();
        conn.commit();
    }

    private static void printTableState(Connection conn) throws SQLException {
        Statement statement1 = conn.createStatement();
        statement1.execute("SELECT * FROM orders");
        ResultSet resultSet1 = statement1.getResultSet();
        Statement statement2 = conn.createStatement();
        statement2.execute("SELECT * FROM point_history");
        ResultSet resultSet2 = statement2.getResultSet();
        while (resultSet1.next()) {
            System.out.println("id = " + resultSet1.getLong("id") + " " + "user_id = " + resultSet1.getLong("user_id"));
        }
        while (resultSet2.next()) {
            System.out.println();
            System.out.println("id = " + resultSet2.getLong("id") + " " + "user_id = " + resultSet2.getLong("user_id"));
        }
    }
}
