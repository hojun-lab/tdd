package com.example.proxy;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class OrderServiceImpl implements OrderService {
    private Connection connection;

    public OrderServiceImpl(Connection connection) {
        this.connection = connection;
    }

    @Override
    public void placeOrder(long userId, long amount) {
        try {
            Statement statement = connection.createStatement();
            statement.execute("INSERT INTO orders(user_id, amount) VALUES (1, 1)");
            throw new RuntimeException();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
//        System.out.println("주문 처리 : userId = " + userId + ", amount = " + amount);
//        this.validateAndSave(userId, amount);
    }

    @Override
    public void validateAndSave(long userId, long amount) {
        System.out.println("valid ==> save : done");
    }
}
