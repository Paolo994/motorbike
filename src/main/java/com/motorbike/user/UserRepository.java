package com.motorbike.user;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Utenti attivi il cui username contiene {@code contains}; prima quelli che iniziano con {@code prefix}.
     * I pattern devono essere già in minuscolo e con i caratteri LIKE escapati con '\'.
     */
    @Query("""
        select u from User u
        where u.enabled = true and u.id <> :excludeId
          and lower(u.username) like :contains escape '\\'
        order by case when lower(u.username) like :prefix escape '\\' then 0 else 1 end, lower(u.username)
        """)
    List<User> searchByUsername(String contains, String prefix, Long excludeId, Pageable page);

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmail(String email);

    List<User> findByRoleAndEnabledTrue(Role role);
}
