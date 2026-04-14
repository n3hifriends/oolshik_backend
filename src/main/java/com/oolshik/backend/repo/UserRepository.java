package com.oolshik.backend.repo;

import com.oolshik.backend.entity.UserEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByFirebaseUid(String firebaseUid);
    Optional<UserEntity> findByPhoneNumber(String phoneNumber);
    Optional<UserEntity> findByEmail(String email);
    @Query("select u from UserEntity u where lower(trim(u.email)) = lower(trim(:email))")
    Optional<UserEntity> findByEmailIgnoreCase(@Param("email") String email);
    boolean existsByPhoneNumber(String phoneNumber);
    boolean existsByEmail(String email);
    @Query("select count(u) > 0 from UserEntity u where lower(trim(u.email)) = lower(trim(:email))")
    boolean existsByEmailIgnoreCase(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.id = :id")
    Optional<UserEntity> findByIdForUpdate(@Param("id") UUID id);
}
