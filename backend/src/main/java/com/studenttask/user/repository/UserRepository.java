package com.studenttask.user.repository;

import com.studenttask.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("select u.timezone from User u where u.id = :id")
    Optional<String> findTimezoneById(@Param("id") long id);
}
