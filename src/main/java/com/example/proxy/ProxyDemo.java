package com.example.proxy;

import java.lang.reflect.Proxy;

public class ProxyDemo {
    public static void main(String[] args) {
        OrderService target = new OrderServiceImpl();
        OrderService proxy = (OrderService) Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                new Class[]{ OrderService.class },
                new LoggingHandler(target)
        );

        System.out.println("프록시 클래스명: " + proxy.getClass().getName());
        proxy.placeOrder(1, 1000L);
    }
}
