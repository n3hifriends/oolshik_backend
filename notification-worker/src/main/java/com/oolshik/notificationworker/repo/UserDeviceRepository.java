package com.oolshik.notificationworker.repo;

import com.oolshik.notificationworker.entity.UserDeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserDeviceRepository extends JpaRepository<UserDeviceEntity, UUID> {

    @Query("""
        select d
          from UserDeviceEntity d
         where d.userId in :userIds
           and d.isActive = true
        """)
    List<UserDeviceEntity> findActiveByUserIds(@Param("userIds") List<UUID> userIds);

    @Modifying
    @Query(value = """
        UPDATE user_device
           SET is_active = false,
               updated_at = now()
         WHERE token_hash = :tokenHash
        """, nativeQuery = true)
    int deactivateByTokenHash(@Param("tokenHash") String tokenHash);
}
