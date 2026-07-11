package com.oolshik.backend.domain;

public enum OnboardingPhase {
    FRESH,
    INTENT_SET,
    FIRST_ACTION,
    GRADUATED;

    public boolean isBefore(OnboardingPhase other) {
        return this.ordinal() < other.ordinal();
    }
}
