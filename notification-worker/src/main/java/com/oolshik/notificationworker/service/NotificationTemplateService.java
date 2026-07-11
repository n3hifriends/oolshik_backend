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

            // ── Task auth ─────────────────────────────────────────────────────
            case TASK_AUTH_REQUESTED -> new NotificationTemplate(
                    "Approve helper?",
                    "A helper wants to take your request. Review now.");
            case TASK_AUTH_APPROVED -> new NotificationTemplate(
                    "Request approved",
                    "The requester approved you. You're now assigned to this request.");
            case TASK_AUTH_REJECTED -> new NotificationTemplate(
                    "Request not assigned",
                    "The requester did not approve this assignment.");
            case TASK_AUTH_TIMEOUT -> role == RecipientRole.REQUESTER
                    ? new NotificationTemplate(
                            "Approval window closed",
                            "You missed the approval window. Your request is open again.")
                    : new NotificationTemplate(
                            "Approval window closed",
                            "The requester didn't respond in time. Look for another request.");

            // ── Task lifecycle ────────────────────────────────────────────────
            case TASK_CREATED -> new NotificationTemplate(
                    "New request near you",
                    "Open the app to help.");
            case TASK_RADIUS_EXPANDED -> new NotificationTemplate(
                    "New request in your area",
                    "You are now eligible to help.");
            case TASK_CANCELLED -> new NotificationTemplate(
                    "Request cancelled",
                    "The requester cancelled this request.");
            case TASK_RELEASED -> new NotificationTemplate(
                    "Helper left the request",
                    "Your request is open again. We'll look for another helper.");
            case TASK_REASSIGNED -> role == RecipientRole.REQUESTER
                    ? new NotificationTemplate(
                            "Finding a new helper",
                            "Your previous helper is no longer available. We're looking for someone new.")
                    : new NotificationTemplate(
                            "No longer assigned",
                            "You have been removed from this request.");
            case TASK_TIMEOUT -> role == RecipientRole.REQUESTER
                    ? new NotificationTemplate(
                            "Request timed out",
                            "No helper responded in time. Try again or expand your radius.")
                    : new NotificationTemplate(
                            "Request no longer active",
                            "This request has expired.");

            // ── Completion ────────────────────────────────────────────────────
            case WORK_MARKED_DONE -> new NotificationTemplate(
                    "Please confirm completion",
                    "Your helper marked this request done. Confirm or report an issue.");
            case COMPLETION_REMINDER_50 -> new NotificationTemplate(
                    "Completion reminder",
                    "Please confirm this request or report an issue before it closes automatically.");
            case COMPLETION_REMINDER_80 -> new NotificationTemplate(
                    "Action needed soon",
                    "Please respond soon. This request will close automatically if no action is taken.");
            case AUTO_COMPLETED_BY_TIMEOUT -> role == RecipientRole.REQUESTER
                    ? new NotificationTemplate(
                            "Request closed automatically",
                            "No response was received. This request was closed automatically.")
                    : new NotificationTemplate(
                            "Request closed automatically",
                            "This request was closed after no response was received.");
            case COMPLETION_CONFIRMED -> new NotificationTemplate(
                    "Request confirmed completed",
                    "The requester confirmed this request is done. Thank you!");
            case COMPLETION_ISSUE_REPORTED -> new NotificationTemplate(
                    "Issue reported",
                    "The requester reported an issue. Please review the request details.");
            case OFFER_UPDATED -> new NotificationTemplate(
                    "Offer updated",
                    "The offer for a request you were considered for has changed.");

            // ── Payment ───────────────────────────────────────────────────────
            case PAYMENT_REQUEST_CREATED -> role == RecipientRole.PAYER
                    ? new NotificationTemplate(
                            "Payment request created",
                            "Open task details to review and complete the payment.")
                    : new NotificationTemplate(
                            "Payment request created",
                            "A payment has been set up for this request. Open to review.");
            case PAYMENT_ACTION_REQUIRED -> new NotificationTemplate(
                    "Payment needed",
                    "Open task details to complete payment via UPI.");
            case PAYMENT_INITIATED -> new NotificationTemplate(
                    "Payment started",
                    "The other party has started a UPI payment. Verify once you receive it.");
            case PAYMENT_MARKED_PAID -> new NotificationTemplate(
                    "Payment marked paid",
                    "Please verify that you received the UPI payment.");
            case PAYMENT_DISPUTED -> new NotificationTemplate(
                    "Payment disputed",
                    "There is a dispute on this payment. Open task details to review.");
            case PAYMENT_EXPIRED -> role == RecipientRole.PAYER
                    ? new NotificationTemplate(
                            "Payment request expired",
                            "The payment window has closed. Open task details to arrange payment.")
                    : new NotificationTemplate(
                            "Payment request expired",
                            "The payment window has closed. You can send a new payment request from task details.");
        };
    }

    private NotificationTemplate marathiTemplate(NotificationEventType type, RecipientRole role) {
        return switch (type) {

            // ── Task auth ─────────────────────────────────────────────────────
            case TASK_AUTH_REQUESTED -> new NotificationTemplate(
                    "कार्यकर्ता मंजूर करायचा?",
                    "एक कार्यकर्ता तुमची विनंती घ्यायला तयार आहे. आत्ता तपासा.");
            case TASK_AUTH_APPROVED -> new NotificationTemplate(
                    "विनंती मंजूर झाली",
                    "विनंतीकर्त्याने तुम्हाला मंजूर केले. तुम्ही या विनंतीवर नेमलेले आहात.");
            case TASK_AUTH_REJECTED -> new NotificationTemplate(
                    "विनंती नियुक्त झाली नाही",
                    "विनंतीकर्त्याने ही नियुक्ती मंजूर केली नाही.");
            case TASK_AUTH_TIMEOUT -> role == RecipientRole.REQUESTER
                    ? new NotificationTemplate(
                            "मंजुरीची वेळ संपली",
                            "मंजुरीची वेळ निघून गेली. विनंती पुन्हा खुली आहे.")
                    : new NotificationTemplate(
                            "मंजुरीची वेळ संपली",
                            "विनंतीकर्त्याने वेळेत प्रतिसाद दिला नाही. दुसरी विनंती पाहा.");

            // ── Task lifecycle ────────────────────────────────────────────────
            case TASK_CREATED -> new NotificationTemplate(
                    "तुमच्या जवळ नवीन विनंती",
                    "मदत करण्यासाठी ॲप उघडा.");
            case TASK_RADIUS_EXPANDED -> new NotificationTemplate(
                    "तुमच्या भागात नवीन विनंती",
                    "आता तुम्ही मदत करण्यासाठी पात्र आहात.");
            case TASK_CANCELLED -> new NotificationTemplate(
                    "विनंती रद्द झाली",
                    "विनंतीकर्त्याने ही विनंती रद्द केली.");
            case TASK_RELEASED -> new NotificationTemplate(
                    "कार्यकर्त्याने विनंती सोडली",
                    "तुमची विनंती पुन्हा खुली आहे. दुसरा कार्यकर्ता शोधत आहोत.");
            case TASK_REASSIGNED -> role == RecipientRole.REQUESTER
                    ? new NotificationTemplate(
                            "नवीन कार्यकर्ता शोधत आहोत",
                            "तुमचा आधीचा कार्यकर्ता उपलब्ध नाही. नवीन शोधत आहोत.")
                    : new NotificationTemplate(
                            "नियुक्ती रद्द झाली",
                            "तुम्हाला या विनंतीवरून काढले आहे.");
            case TASK_TIMEOUT -> role == RecipientRole.REQUESTER
                    ? new NotificationTemplate(
                            "विनंतीची वेळ संपली",
                            "कोणत्याही कार्यकर्त्याने वेळेत प्रतिसाद दिला नाही. पुन्हा प्रयत्न करा.")
                    : new NotificationTemplate(
                            "विनंती संपली",
                            "ही विनंती कालबाह्य झाली आहे.");

            // ── Completion ────────────────────────────────────────────────────
            case WORK_MARKED_DONE -> new NotificationTemplate(
                    "पूर्णतेची पुष्टी करा",
                    "तुमच्या कार्यकर्त्याने विनंती पूर्ण केली. पुष्टी करा किंवा समस्या नोंदवा.");
            case COMPLETION_REMINDER_50 -> new NotificationTemplate(
                    "पूर्णतेची आठवण",
                    "ही विनंती आपोआप बंद होण्यापूर्वी पुष्टी करा किंवा समस्या नोंदवा.");
            case COMPLETION_REMINDER_80 -> new NotificationTemplate(
                    "लवकर कृती करा",
                    "कृपया लवकर प्रतिसाद द्या. अन्यथा विनंती आपोआप बंद होईल.");
            case AUTO_COMPLETED_BY_TIMEOUT -> role == RecipientRole.REQUESTER
                    ? new NotificationTemplate(
                            "विनंती आपोआप बंद झाली",
                            "कोणताही प्रतिसाद न मिळाल्याने विनंती आपोआप बंद झाली.")
                    : new NotificationTemplate(
                            "विनंती आपोआप बंद झाली",
                            "प्रतिसाद न मिळाल्याने ही विनंती बंद झाली.");
            case COMPLETION_CONFIRMED -> new NotificationTemplate(
                    "विनंती पूर्ण म्हणून पुष्टी झाली",
                    "विनंतीकर्त्याने ही विनंती पूर्ण झाल्याची पुष्टी केली. धन्यवाद!");
            case COMPLETION_ISSUE_REPORTED -> new NotificationTemplate(
                    "समस्या नोंदवली गेली",
                    "विनंतीकर्त्याने समस्या नोंदवली आहे. कृपया विनंतीचे तपशील तपासा.");
            case OFFER_UPDATED -> new NotificationTemplate(
                    "ऑफर अद्ययावत झाली",
                    "तुम्ही विचारात असलेल्या विनंतीसाठी ऑफर बदलली आहे.");

            // ── Payment ───────────────────────────────────────────────────────
            case PAYMENT_REQUEST_CREATED -> role == RecipientRole.PAYER
                    ? new NotificationTemplate(
                            "पेमेंट विनंती तयार झाली",
                            "पेमेंट पूर्ण करण्यासाठी विनंतीचे तपशील उघडा.")
                    : new NotificationTemplate(
                            "पेमेंट विनंती तयार झाली",
                            "या विनंतीसाठी पेमेंट तयार केले आहे. उघडून तपासा.");
            case PAYMENT_ACTION_REQUIRED -> new NotificationTemplate(
                    "पेमेंट आवश्यक आहे",
                    "UPI पेमेंट पूर्ण करण्यासाठी विनंतीचे तपशील उघडा.");
            case PAYMENT_INITIATED -> new NotificationTemplate(
                    "पेमेंट सुरू झाले",
                    "दुसऱ्या बाजूने UPI पेमेंट सुरू केले आहे. मिळाल्यावर तपासा.");
            case PAYMENT_MARKED_PAID -> new NotificationTemplate(
                    "पेमेंट पूर्ण म्हणून नोंदले",
                    "UPI पेमेंट मिळाले आहे का ते कृपया तपासा.");
            case PAYMENT_DISPUTED -> new NotificationTemplate(
                    "पेमेंटवर वाद नोंदला",
                    "या पेमेंटवर वाद आहे. तपशील तपासण्यासाठी विनंती उघडा.");
            case PAYMENT_EXPIRED -> role == RecipientRole.PAYER
                    ? new NotificationTemplate(
                            "पेमेंट विनंतीची मुदत संपली",
                            "पेमेंटची वेळ संपली. पेमेंटची व्यवस्था करण्यासाठी विनंतीचे तपशील उघडा.")
                    : new NotificationTemplate(
                            "पेमेंट विनंतीची मुदत संपली",
                            "पेमेंटची वेळ संपली. तुम्ही विनंतीच्या तपशीलातून नवीन पेमेंट विनंती पाठवू शकता.");
        };
    }

    public record NotificationTemplate(String title, String body) {}

    public enum RecipientRole {
        REQUESTER,
        HELPER,
        CANDIDATE_HELPER,
        PAYER,
        PAYEE
    }
}
