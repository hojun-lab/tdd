package com.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class RawJdbcExperiment {

    // H2 인메모리 DB 설정 — 파일로 유지하려면 jdbc:h2:file:./testdb 로 변경
    private static final String URL = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1";
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    public static void main(String[] args) throws SQLException {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            setupTables(conn);

            System.out.println("=== Case 1: autocommit=true (기본값) ===");
            // TODO: 여기서 실험하라

            System.out.println("=== Case 2: autocommit=false, rollback 없음 ===");
            // TODO: 여기서 실험하라

            System.out.println("=== Case 3: autocommit=false + rollback() ===");
            // TODO: 여기서 실험하라
        }
    }

    private static void setupTables(Connection conn) throws SQLException {
        // TODO: orders 테이블과 point_history 테이블을 CREATE TABLE로 만들어라
    }

    private static void printTableState(Connection conn) throws SQLException {
        // TODO: 각 케이스 실험 후 테이블 데이터를 조회해서 출력하라
    }
}
