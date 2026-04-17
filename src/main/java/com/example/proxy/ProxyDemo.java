package com.example.proxy;

import java.lang.reflect.Proxy;
import java.sql.*;

public class ProxyDemo {
    private static final String URL = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1";
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    public static void main(String[] args) {
        try {
            Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
            setupTables(conn);
            OrderService target = new OrderServiceImpl(conn);
            OrderService proxy = (OrderService) Proxy.newProxyInstance(
                    target.getClass().getClassLoader(),
                    new Class[]{ OrderService.class },
                    new TransactionHandler(target, conn)
            );

            System.out.println("프록시 클래스명: " + proxy.getClass().getName());
            proxy.placeOrder(1, 1000L);
            printTableState(conn);
        } catch (SQLException e) {
            throw new RuntimeException(e);
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
