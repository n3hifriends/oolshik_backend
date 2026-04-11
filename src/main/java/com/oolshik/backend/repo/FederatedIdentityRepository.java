package com.oolshik.backend.repo;

import com.oolshik.backend.entity.FederatedIdentityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FederatedIdentityRepository extends JpaRepository<FederatedIdentityEntity, UUID> {
    Optional<FederatedIdentityEntity> findByProviderAndProviderSubject(String provider, String providerSubject);
    Optional<FederatedIdentityEntity> findByUserIdAndProvider(UUID userId, String provider);
}
