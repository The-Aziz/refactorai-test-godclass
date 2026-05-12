package com.refactorai.test;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

public class UserAccountManagerTest {

    @Test
    public void testUserCreationAndAuthentication() {
        UserAccountManager manager = new UserAccountManager();
        assertTrue(manager.createUser("jdoe", "password123", "jdoe@example.com"));
        assertFalse(manager.createUser("jdoe", "password123", "jdoe@example.com")); // Duplicate

        assertTrue(manager.authenticate("jdoe", "password123"));
        assertFalse(manager.authenticate("jdoe", "wrongpassword"));
    }

    @Test
    public void testOrderAndProductManagement() {
        UserAccountManager manager = new UserAccountManager();
        manager.addProduct("p1", "Laptop", 1000.0, 10, "Electronics");

        manager.createUser("jdoe", "password123", "jdoe@example.com");
        manager.createOrder("jdoe", Arrays.asList("p1"));

        List<Order> orders = manager.getUserOrders("jdoe");
        assertEquals(1, orders.size());
        assertEquals("PENDING", orders.get(0).status);
        assertEquals(1000.0 + 1000.0 * 0.1 - 1000.0 * 0.05, orders.get(0).totalAmount);
    }

    @Test
    public void testReporting() {
        UserAccountManager manager = new UserAccountManager();
        manager.createUser("jdoe", "password123", "jdoe@example.com");
        String report = manager.generateUserReport();
        assertTrue(report.contains("jdoe"));
        assertTrue(report.contains("jdoe@example.com"));
    }
}
