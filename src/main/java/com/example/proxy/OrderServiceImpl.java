package com.example.proxy;

public class OrderServiceImpl implements OrderService {
    @Override
    public void placeOrder(long userId, long amount) {
        System.out.println("주문 처리 : userId = " + userId + ", amount = " + amount);
    }
}
