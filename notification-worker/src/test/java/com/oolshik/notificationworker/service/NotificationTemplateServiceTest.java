package com.oolshik.notificationworker.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationTemplateServiceTest {

    private final NotificationTemplateService service = new NotificationTemplateService();

    @Test
    void returnsMarathiTemplateForMrLocale() {
        NotificationTemplateService.NotificationTemplate template = service.templateFor(
                "TASK_CREATED",
                NotificationTemplateService.RecipientRole.HELPER,
                "mr-IN"
        );
        assertEquals("तुमच्या जवळ नवीन विनंती", template.title());
    }

    @Test
    void fallsBackToEnglishForUnsupportedLocale() {
        NotificationTemplateService.NotificationTemplate template = service.templateFor(
                "TASK_CREATED",
                NotificationTemplateService.RecipientRole.HELPER,
                "de-DE"
        );
        assertEquals("New request near you", template.title());
    }
}

