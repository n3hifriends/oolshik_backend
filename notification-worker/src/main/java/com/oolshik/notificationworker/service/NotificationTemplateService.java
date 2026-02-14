package com.oolshik.notificationworker.service;

import com.oolshik.notificationworker.model.NotificationEventType;
import org.springframework.stereotype.Service;

@Service
public class NotificationTemplateService {

    public NotificationTemplate templateFor(String eventType, RecipientRole role) {
        NotificationEventType type = NotificationEventType.valueOf(eventType);
        return switch (type) {
            case TASK_AUTH_REQUESTED -> new NotificationTemplate("Verification needed", "Please verify to proceed.");
            case TASK_AUTH_APPROVED -> new NotificationTemplate("Verified", "You may proceed with the request.");
            case TASK_AUTH_REJECTED -> new NotificationTemplate("Verification failed", "Try again or contact support.");
            case TASK_AUTH_TIMEOUT -> new NotificationTemplate("Verification timed out", "Please restart verification.");
            case TASK_CANCELLED -> new NotificationTemplate("Request cancelled", "The request is no longer active.");
            case TASK_RELEASED -> new NotificationTemplate("Helper released", "Your request is available again.");
            case TASK_REASSIGNED -> new NotificationTemplate("Request reassigned", "Assignment has changed.");
            case TASK_TIMEOUT -> new NotificationTemplate("Request timed out", "Please retry or expand radius.");
            case TASK_CREATED -> new NotificationTemplate("New request near you", "Open the app to help.");
            case TASK_RADIUS_EXPANDED -> new NotificationTemplate("New request in your area", "You are now eligible to help.");
            case PAYMENT_REQUEST_CREATED -> new NotificationTemplate("Payment request created", "A payment request is linked to your task.");
            case PAYMENT_ACTION_REQUIRED -> new NotificationTemplate(
                    role == RecipientRole.REQUESTER ? "Payment needed" : "Please complete payment",
                    role == RecipientRole.REQUESTER
                            ? "A helper requested payment. Open task details to pay via UPI."
                            : "Open task details to complete payment via UPI."
            );
            case PAYMENT_INITIATED -> new NotificationTemplate("Payment started", "UPI payment has been initiated.");
            case PAYMENT_MARKED_PAID -> new NotificationTemplate("Payment marked paid", "Please verify the transfer and proceed.");
            case PAYMENT_DISPUTED -> new NotificationTemplate("Payment disputed", "There is an issue with this payment. Please review.");
            case PAYMENT_EXPIRED -> new NotificationTemplate("Payment request expired", "Create a new payment request to continue.");
        };
    }

    public record NotificationTemplate(String title, String body) {}

    public enum RecipientRole {
        REQUESTER,
        HELPER
    }
}
