package com.example.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.sql.Connection;

public class TransactionHandler implements InvocationHandler {
    private final OrderService target;
    private final Connection connection;

    public TransactionHandler(OrderService target, Connection connection) {
        this.target = target;
        this.connection = connection;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        System.out.println("[BEGIN] ================== ");
        connection.setAutoCommit(false);
        try {
            Object result = method.invoke(target, args);
            System.out.println("[COMMIT] ================== ");
            connection.commit();
            return result;
        } catch (Exception e) {
            System.out.println("[ROLLBACK] ================== ");
            connection.rollback();
            throw e;
        }
    }
}
