package com.oolshik.notificationworker.service;

import com.oolshik.notificationworker.model.NotificationEventType;
import org.springframework.stereotype.Service;

@Service
public class NotificationTemplateService {

    public NotificationTemplate templateFor(String eventType, RecipientRole role) {
        return templateFor(eventType, role, LocaleSupport.EN_IN_TAG);
    }

    public NotificationTemplate templateFor(String eventType, RecipientRole role, String localeTag) {
        NotificationEventType type = NotificationEventType.valueOf(eventType);
        if (LocaleSupport.isMarathi(localeTag)) {
            return marathiTemplate(type, role);
        }
        return englishTemplate(type, role);
    }

    private NotificationTemplate englishTemplate(NotificationEventType type, RecipientRole role) {
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
            case WORK_MARKED_DONE -> new NotificationTemplate(
                    "Please confirm completion",
                    "Your helper marked this task as completed. Please confirm or report an issue."
            );
            case COMPLETION_REMINDER_50 -> new NotificationTemplate(
                    "Completion reminder",
                    "Please confirm this task or report an issue before it closes automatically."
            );
            case COMPLETION_REMINDER_80 -> new NotificationTemplate(
                    "Action needed soon",
                    "Please respond soon. This task will close automatically if no action is taken."
            );
            case AUTO_COMPLETED_BY_TIMEOUT -> new NotificationTemplate(
                    "Task closed automatically",
                    "This task was completed automatically after no response."
            );
            case COMPLETION_CONFIRMED -> new NotificationTemplate(
                    "Task confirmed completed",
                    "The requester confirmed this task is completed."
            );
            case COMPLETION_ISSUE_REPORTED -> new NotificationTemplate(
                    "Issue reported",
                    "The requester reported an issue for this task."
            );
            case OFFER_UPDATED -> new NotificationTemplate("Offer updated", "A requester updated the offer for a nearby task.");
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

    private NotificationTemplate marathiTemplate(NotificationEventType type, RecipientRole role) {
        return switch (type) {
            case TASK_AUTH_REQUESTED -> new NotificationTemplate("मंजुरी आवश्यक", "पुढे जाण्यासाठी कृपया मंजुरी द्या.");
            case TASK_AUTH_APPROVED -> new NotificationTemplate("मंजुरी मिळाली", "आता तुम्ही विनंतीवर पुढे जाऊ शकता.");
            case TASK_AUTH_REJECTED -> new NotificationTemplate("मंजुरी नाकारली", "पुन्हा प्रयत्न करा किंवा सहाय्याशी संपर्क करा.");
            case TASK_AUTH_TIMEOUT -> new NotificationTemplate("मंजुरी वेळ संपला", "कृपया पुन्हा मंजुरी प्रक्रिया सुरू करा.");
            case TASK_CANCELLED -> new NotificationTemplate("विनंती रद्द झाली", "ही विनंती आता सक्रिय नाही.");
            case TASK_RELEASED -> new NotificationTemplate("कार्यकर्ता मुक्त झाला", "तुमची विनंती पुन्हा उपलब्ध आहे.");
            case TASK_REASSIGNED -> new NotificationTemplate("विनंती पुनर्नियुक्त झाली", "नेमणुकीत बदल झाला आहे.");
            case TASK_TIMEOUT -> new NotificationTemplate("विनंतीची वेळ संपली", "कृपया पुन्हा प्रयत्न करा किंवा परिघ वाढवा.");
            case TASK_CREATED -> new NotificationTemplate("तुमच्या जवळ नवीन विनंती", "मदत करण्यासाठी अ‍ॅप उघडा.");
            case TASK_RADIUS_EXPANDED -> new NotificationTemplate("तुमच्या भागात नवीन विनंती", "आता तुम्ही मदत करण्यासाठी पात्र आहात.");
            case WORK_MARKED_DONE -> new NotificationTemplate(
                    "पूर्णतेची पुष्टी करा",
                    "तुमच्या कार्यकर्त्याने हे काम पूर्ण केल्याचे दर्शवले आहे. कृपया पुष्टी करा किंवा समस्या नोंदवा."
            );
            case COMPLETION_REMINDER_50 -> new NotificationTemplate(
                    "पूर्णतेची आठवण",
                    "कृपया हे काम पुष्टी करा किंवा समस्या नोंदवा. अन्यथा हे आपोआप बंद होईल."
            );
            case COMPLETION_REMINDER_80 -> new NotificationTemplate(
                    "लवकर कृती करा",
                    "कृपया लवकर प्रतिसाद द्या. अन्यथा हे काम आपोआप पूर्ण म्हणून बंद होईल."
            );
            case AUTO_COMPLETED_BY_TIMEOUT -> new NotificationTemplate(
                    "काम आपोआप पूर्ण झाले",
                    "कोणताही प्रतिसाद न मिळाल्याने हे काम आपोआप पूर्ण म्हणून बंद झाले."
            );
            case COMPLETION_CONFIRMED -> new NotificationTemplate(
                    "काम पूर्ण म्हणून पुष्टी झाले",
                    "नेत्याने हे काम पूर्ण झाल्याची पुष्टी केली आहे."
            );
            case COMPLETION_ISSUE_REPORTED -> new NotificationTemplate(
                    "समस्या नोंदवली गेली",
                    "नेत्याने या कामाबद्दल समस्या नोंदवली आहे."
            );
            case OFFER_UPDATED -> new NotificationTemplate("ऑफर अद्ययावत", "जवळील कामासाठी विनंतीकर्त्याने ऑफर बदलली आहे.");
            case PAYMENT_REQUEST_CREATED -> new NotificationTemplate("पेमेंट विनंती तयार झाली", "तुमच्या कामाशी पेमेंट विनंती जोडली गेली आहे.");
            case PAYMENT_ACTION_REQUIRED -> new NotificationTemplate(
                    role == RecipientRole.REQUESTER ? "पेमेंट आवश्यक" : "कृपया पेमेंट पूर्ण करा",
                    role == RecipientRole.REQUESTER
                            ? "कार्यकर्त्याने पेमेंट विनंती केली आहे. UPI पेमेंटसाठी टास्क तपशील उघडा."
                            : "UPI पेमेंट पूर्ण करण्यासाठी टास्क तपशील उघडा."
            );
            case PAYMENT_INITIATED -> new NotificationTemplate("पेमेंट सुरू झाले", "UPI पेमेंट सुरू करण्यात आले आहे.");
            case PAYMENT_MARKED_PAID -> new NotificationTemplate("पेमेंट पूर्ण म्हणून नोंदले", "कृपया व्यवहार तपासा आणि पुढे जा.");
            case PAYMENT_DISPUTED -> new NotificationTemplate("पेमेंटवर वाद नोंदला", "या पेमेंटमध्ये समस्या आहे. कृपया तपासा.");
            case PAYMENT_EXPIRED -> new NotificationTemplate("पेमेंट विनंतीची मुदत संपली", "पुढे सुरू ठेवण्यासाठी नवीन पेमेंट विनंती तयार करा.");
        };
    }

    public record NotificationTemplate(String title, String body) {}

    public enum RecipientRole {
        REQUESTER,
        HELPER
    }
}
