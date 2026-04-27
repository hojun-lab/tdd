package com.example.tx;

import java.sql.Connection;

public class ConnectionHolder {
    private static final ThreadLocal<Connection> connectionHolder = new ThreadLocal<>();

    public static void saveThreadConnection(Connection connection) {
        connectionHolder.set(connection);
    }

    public static Connection getThreadConnection() {
        return connectionHolder.get();
    }

    public static void deleteThreadConnection() {
        connectionHolder.remove();
    }
}
