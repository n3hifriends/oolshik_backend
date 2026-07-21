package com.oolshik.backend.service;

import com.oolshik.backend.domain.OnboardingPhase;
import com.oolshik.backend.entity.UserEntity;
import com.oolshik.backend.repo.UserRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class UserServiceTest {

    private final UserService service = new UserService(mock(UserRepository.class));

    @Test
    void advanceOnboardingPhaseIfNeededAdvancesFromNullToTarget() {
        UserEntity user = new UserEntity();

        boolean advanced = service.advanceOnboardingPhaseIfNeeded(user, OnboardingPhase.INTENT_SET);

        assertTrue(advanced);
        assertEquals(OnboardingPhase.INTENT_SET, user.getOnboardingPhase());
    }

    @Test
    void advanceOnboardingPhaseIfNeededAdvancesIntentSetToFirstAction() {
        UserEntity user = new UserEntity();
        user.setOnboardingPhase(OnboardingPhase.INTENT_SET);

        boolean advanced = service.advanceOnboardingPhaseIfNeeded(user, OnboardingPhase.FIRST_ACTION);

        assertTrue(advanced);
        assertEquals(OnboardingPhase.FIRST_ACTION, user.getOnboardingPhase());
    }

    @Test
    void advanceOnboardingPhaseIfNeededNoOpsWhenAlreadyAtTarget() {
        UserEntity user = new UserEntity();
        user.setOnboardingPhase(OnboardingPhase.FIRST_ACTION);

        boolean advanced = service.advanceOnboardingPhaseIfNeeded(user, OnboardingPhase.FIRST_ACTION);

        assertFalse(advanced);
        assertEquals(OnboardingPhase.FIRST_ACTION, user.getOnboardingPhase());
    }

    @Test
    void advanceOnboardingPhaseIfNeededNoOpsWhenPastTarget() {
        UserEntity user = new UserEntity();
        user.setOnboardingPhase(OnboardingPhase.GRADUATED);

        boolean advanced = service.advanceOnboardingPhaseIfNeeded(user, OnboardingPhase.FIRST_ACTION);

        assertFalse(advanced);
        assertEquals(OnboardingPhase.GRADUATED, user.getOnboardingPhase());
    }
}
