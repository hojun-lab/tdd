package com.example.proxy;

public interface OrderService {
    void placeOrder(long userId, long amount);
    void validateAndSave(long userId, long amount);
}
