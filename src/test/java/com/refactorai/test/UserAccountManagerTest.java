package com.refactorai.test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

public class UserAccountManagerTest {
    private UserAccountManager manager;

    @BeforeEach
    public void setUp() {
        manager = new UserAccountManager();
    }

    @Test
    public void testUserLifecycle() {
        assertTrue(manager.createUser("jdoe", "password123", "jdoe@example.com"));
        assertFalse(manager.createUser("jdoe", "other", "other@example.com"));

        Map<String, String> profile = manager.getUserProfile("jdoe");
        assertEquals("jdoe@example.com", profile.get("email"));
        assertEquals("user", profile.get("role"));

        assertTrue(manager.authenticate("jdoe", "password123"));
        assertFalse(manager.authenticate("jdoe", "wrong"));

        assertTrue(manager.updateUserRole("jdoe", "admin"));
        assertTrue(manager.isUserAdmin("jdoe"));

        assertTrue(manager.deleteUser("jdoe"));
        assertNull(manager.getUserProfile("jdoe"));
    }

    @Test
    public void testOrderProcessing() {
        manager.addProduct("p1", "Laptop", 1000.0, 10, "Electronics");
        manager.createUser("buyer", "pass", "buyer@example.com");

        manager.createOrder("buyer", Arrays.asList("p1"));
        List<Order> orders = manager.getUserOrders("buyer");
        assertEquals(1, orders.size());
        assertEquals(1000.0 + 1000.0 * 0.1 - (1000.0 * 0.05), orders.get(0).totalAmount, 0.01);

        Map<String, String> profile = manager.getUserProfile("buyer");
        assertEquals("1", profile.get("orderCount"));

        manager.cancelOrder(orders.get(0).orderId);
        assertEquals("CANCELLED", manager.getUserOrders("buyer").get(0).status);
    }

    @Test
    public void testReportingAndAnalytics() {
        manager.addProduct("p1", "A", 10.0, 10, "Cat1");
        manager.createUser("u1", "p", "u1@e.com");
        manager.createOrder("u1", Arrays.asList("p1"));

        String report = manager.generateUserReport();
        assertTrue(report.contains("u1"));

        Map<String, Double> revenue = manager.getRevenueByCategory();
        assertEquals(10.0, revenue.get("Cat1"), 0.01);

        Map<String, Object> stats = manager.getDashboardStats();
        assertEquals(1, stats.get("totalUsers"));
        assertEquals(1, stats.get("totalOrders"));
    }

    @Test
    public void testConfigAndCache() {
        manager.clearAllCache();
        manager.createUser("u1", "p", "u1@e.com");
        // listAllUsers uses cache
        assertEquals(1, manager.listAllUsers().size());

        Map<String, String> config = manager.getConfig();
        assertEquals("smtp.example.com", config.get("smtp.host"));
    }
}
