package com.studenttask.user.repository;

import com.studenttask.user.entity.User;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, long id);

    @Query("select u.timezone from User u where u.id = :id")
    Optional<String> findTimezoneById(@Param("id") long id);

    @Query("select u.id from User u where u.telegramChatId = :chatId")
    Optional<Long> findIdByTelegramChatId(@Param("chatId") long chatId);

    @Query("select u.email from User u where u.telegramChatId = :chatId")
    Optional<String> findEmailByTelegramChatId(@Param("chatId") long chatId);

    /** Null when not connected (or no such user). The chat id never leaves the backend through the entity. */
    @Query("select u.telegramChatId from User u where u.id = :id")
    Optional<Long> findTelegramChatIdById(@Param("id") long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.telegramChatId = :chatId, u.telegramConnectedAt = :now, u.updatedAt = :now where u.id = :id")
    int linkTelegram(@Param("id") long id, @Param("chatId") long chatId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.telegramChatId = null, u.telegramConnectedAt = null, u.updatedAt = :now where u.id = :id")
    int unlinkTelegram(@Param("id") long id, @Param("now") Instant now);
}
