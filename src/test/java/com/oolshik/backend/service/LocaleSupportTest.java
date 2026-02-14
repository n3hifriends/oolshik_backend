package com.oolshik.backend.service;

import com.oolshik.backend.config.LocaleSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocaleSupportTest {

    @Test
    void normalizesLanguageVariants() {
        assertEquals("mr-IN", LocaleSupport.normalizeTag("mr"));
        assertEquals("mr-IN", LocaleSupport.normalizeTag("mr_IN"));
        assertEquals("en-IN", LocaleSupport.normalizeTag("en-US"));
        assertEquals("en-IN", LocaleSupport.normalizeTag("unknown"));
    }
}

