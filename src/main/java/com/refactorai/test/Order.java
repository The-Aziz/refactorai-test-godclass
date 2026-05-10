package com.refactorai.test;

import java.util.*;

public class Order {
    public String orderId;
    public String userId;
    public double totalAmount;
    public String status;
    public Date orderDate;
    public List<String> items;
    public String shippingAddress;
    public String paymentMethod;
    public double discount;
    public double tax;
    
    public Order() {
        this.items = new ArrayList<>();
        this.orderDate = new Date();
    }
}
