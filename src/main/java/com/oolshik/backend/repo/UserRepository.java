package com.oolshik.backend.repo;

import com.oolshik.backend.entity.UserEntity;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID>, JpaSpecificationExecutor<UserEntity> {
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

    default Page<UserEntity> findForAdmin(String role, String search, Boolean blocked, Pageable pageable) {
        return findAll(adminSpec(role, search, blocked), pageable);
    }

    default Page<UserEntity> findForAdminByRole(String role, Pageable pageable) {
        return findForAdmin(role, null, null, pageable);
    }

    static Specification<UserEntity> adminSpec(String role, String search, Boolean blocked) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (role != null && !role.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("roles")), "%" + role.toLowerCase() + "%"));
            }
            if (search != null && !search.isBlank()) {
                String nameLike = "%" + search.toLowerCase() + "%";
                String phoneLike = "%" + search + "%";
                predicates.add(cb.or(
                    cb.and(cb.isNotNull(root.get("displayName")), cb.like(cb.lower(root.get("displayName")), nameLike)),
                    cb.and(cb.isNotNull(root.get("phoneNumber")), cb.like(root.get("phoneNumber"), phoneLike)),
                    cb.and(cb.isNotNull(root.get("email")), cb.like(cb.lower(root.get("email")), nameLike))
                ));
            }
            if (blocked != null) {
                predicates.add(cb.equal(root.get("blocked"), blocked));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

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
