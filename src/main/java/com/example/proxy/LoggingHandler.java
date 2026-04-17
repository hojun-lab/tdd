package com.example.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class LoggingHandler implements InvocationHandler {
    private final OrderService orderService;

    public LoggingHandler(OrderService target) {
        this.orderService = target;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        System.out.println("[BEFORE] " + method.getName());
        method.invoke(orderService, args);
        System.out.println("[AFTER] " + method.getName());
        return proxy;
    }
}
