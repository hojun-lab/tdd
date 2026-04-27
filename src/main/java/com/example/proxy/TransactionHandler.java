package com.example.proxy;

import com.example.tx.ConnectionHolder;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.sql.Connection;

public class TransactionHandler implements InvocationHandler {
    private final OrderService target;
    private final DataSource dataSource;

    public TransactionHandler(OrderService target, DataSource dataSource) {
        this.target = target;
        this.dataSource = dataSource;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        System.out.println("[BEGIN] ================== ");
        Connection connection = dataSource.getConnection();
        System.out.println("HASHING = " + System.identityHashCode(connection));
        ConnectionHolder.saveThreadConnection(connection);
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
        } finally {
            ConnectionHolder.deleteThreadConnection();
        }
    }
}
