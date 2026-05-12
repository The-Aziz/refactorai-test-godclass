package com.refactorai.test;

public class EmailService {
    private LoggingService loggingService;
    private UserService userService;

    public EmailService(LoggingService loggingService) {
        this.loggingService = loggingService;
    }

    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    public void sendWelcomeEmail(String username, String email) {
        String subject = "Welcome to Our Platform, " + username + "!";
        String body = "Dear " + username + ",\n\nThank you for registering.\n\nBest regards,\nThe Team";
        sendEmail(email, subject, body);
    }

    public void sendEmailChangeNotification(String username, String oldEmail, String newEmail) {
        sendEmail(oldEmail, "Email Changed", "Your email was changed to " + newEmail);
        sendEmail(newEmail, "Email Confirmed", "Welcome to your new email, " + username);
    }

    public void sendPasswordChangeNotification(String username) {
        if (userService == null) return;
        String email = userService.getUserEmail(username);
        if (email != null) {
            sendEmail(email, "Password Changed", "Your password was recently changed.");
        }
    }

    public void sendEmail(String to, String subject, String body) {
        if (loggingService != null) loggingService.log("INFO", "Sending email to " + to + ": " + subject);
        try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        if (loggingService != null) loggingService.log("INFO", "Email sent to " + to);
    }
}
