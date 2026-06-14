package com.oolshik.backend.repo;

import com.oolshik.backend.entity.UserEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.List;
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

    default Page<UserEntity> findForAdmin(String role, String search, Pageable pageable) {
        boolean hasRole = role != null && !role.isBlank();
        boolean hasSearch = search != null && !search.isBlank();
        if (hasRole && hasSearch) {
            return findForAdminByRoleAndSearch(role, search, pageable);
        }
        if (hasRole) {
            return findForAdminByRole(role, pageable);
        }
        if (hasSearch) {
            return findForAdminBySearch(search, pageable);
        }
        return findAll(pageable);
    }

    @Query(value = """
            select u from UserEntity u
            where lower(u.roles) like lower(concat('%', :role, '%'))
              and ((u.displayName is not null and lower(u.displayName) like lower(concat('%', :search, '%')))
                   or (u.phoneNumber is not null and u.phoneNumber like concat('%', :search, '%'))
                   or (u.email is not null and lower(u.email) like lower(concat('%', :search, '%'))))
            """,
            countQuery = """
            select count(u) from UserEntity u
            where lower(u.roles) like lower(concat('%', :role, '%'))
              and ((u.displayName is not null and lower(u.displayName) like lower(concat('%', :search, '%')))
                   or (u.phoneNumber is not null and u.phoneNumber like concat('%', :search, '%'))
                   or (u.email is not null and lower(u.email) like lower(concat('%', :search, '%'))))
            """)
    Page<UserEntity> findForAdminByRoleAndSearch(@Param("role") String role,
                                                 @Param("search") String search,
                                                 Pageable pageable);

    @Query("select u from UserEntity u where lower(u.roles) like lower(concat('%', :role, '%'))")
    Page<UserEntity> findForAdminByRole(@Param("role") String role, Pageable pageable);

    @Query(value = """
            select u from UserEntity u
            where (u.displayName is not null and lower(u.displayName) like lower(concat('%', :search, '%')))
               or (u.phoneNumber is not null and u.phoneNumber like concat('%', :search, '%'))
               or (u.email is not null and lower(u.email) like lower(concat('%', :search, '%')))
            """,
            countQuery = """
            select count(u) from UserEntity u
            where (u.displayName is not null and lower(u.displayName) like lower(concat('%', :search, '%')))
               or (u.phoneNumber is not null and u.phoneNumber like concat('%', :search, '%'))
               or (u.email is not null and lower(u.email) like lower(concat('%', :search, '%')))
            """)
    Page<UserEntity> findForAdminBySearch(@Param("search") String search, Pageable pageable);

    @Query("select count(u) from UserEntity u where lower(u.roles) like lower(concat('%', :role, '%'))")
    long countByRolesContaining(@Param("role") String role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select u from UserEntity u
            where lower(u.roles) like lower(concat('%', :role, '%'))
            order by u.id
            """)
    List<UserEntity> lockUsersByRoleForUpdate(@Param("role") String role);
}
