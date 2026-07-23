package com.oolshik.backend.service;

import com.oolshik.backend.entity.UserEntity;
import com.oolshik.backend.repo.FederatedIdentityRepository;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.web.error.ConflictOperationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceDeleteAccountTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private FederatedIdentityRepository federatedIdentityRepository;

    @Test
    void deleteOwnAccountFlagsUserAndClearsUniqueIdentifiers() {
        UserService service = new UserService(userRepository, federatedIdentityRepository);
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setPhoneNumber("+919876543210");
        user.setEmail("someone@example.com");
        user.setFirebaseUid("firebase-uid");
        user.setPasswordHash("hash");

        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        service.deleteOwnAccount(userId);

        assertTrue(user.isDeleted());
        assertNotNull(user.getDeletedAt());
        assertNull(user.getPhoneNumber());
        assertNull(user.getEmail());
        assertNull(user.getFirebaseUid());
        assertNull(user.getPasswordHash());
        verify(federatedIdentityRepository).deleteByUserId(userId);
    }

    @Test
    void deleteOwnAccountRejectsAlreadyDeletedUser() {
        UserService service = new UserService(userRepository, federatedIdentityRepository);
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setDeleted(true);

        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

        assertThrows(ConflictOperationException.class, () -> service.deleteOwnAccount(userId));
    }

    @Test
    void deleteOwnAccountRejectsMissingUser() {
        UserService service = new UserService(userRepository, federatedIdentityRepository);
        UUID userId = UUID.randomUUID();

        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> service.deleteOwnAccount(userId));
    }
}
