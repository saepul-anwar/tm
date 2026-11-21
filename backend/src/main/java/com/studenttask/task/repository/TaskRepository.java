package com.studenttask.task.repository;

import com.studenttask.task.entity.Task;
import com.studenttask.task.entity.TaskStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    /** The only lookup by id: ownership is part of the query so another user's task is simply absent. */
    Optional<Task> findByIdAndUserId(Long id, long userId);

    @Query("select distinct t.subject from Task t where t.userId = :userId and t.subject is not null "
            + "order by t.subject")
    List<String> findDistinctSubjects(@Param("userId") long userId);

    /** One row per status present for the user: {status, count}. */
    @Query("select t.status, count(t) from Task t where t.userId = :userId group by t.status")
    List<Object[]> countByStatus(@Param("userId") long userId);

    /** Open (not COMPLETED) tasks with deadline strictly before {@code now}. */
    @Query("select count(t) from Task t where t.userId = :userId and t.status <> :done and t.deadline < :now")
    long countOverdue(@Param("userId") long userId, @Param("done") TaskStatus done, @Param("now") Instant now);

    /** Open tasks with deadline in [from, to] (both inclusive). */
    @Query("select count(t) from Task t where t.userId = :userId and t.status <> :done "
            + "and t.deadline >= :from and t.deadline <= :to")
    long countOpenDueBetween(@Param("userId") long userId, @Param("done") TaskStatus done,
            @Param("from") Instant from, @Param("to") Instant to);

    @Query("select t from Task t where t.userId = :userId and t.status <> :done "
            + "and t.deadline >= :from and t.deadline <= :to order by t.deadline asc, t.id asc")
    List<Task> findOpenDueBetween(@Param("userId") long userId, @Param("done") TaskStatus done,
            @Param("from") Instant from, @Param("to") Instant to, Pageable limit);

    @Query("select t from Task t where t.userId = :userId and t.status <> :done and t.deadline < :now "
            + "order by t.deadline asc, t.id asc")
    List<Task> findOverdue(@Param("userId") long userId, @Param("done") TaskStatus done,
            @Param("now") Instant now, Pageable limit);

    @Query("select t from Task t where t.userId = :userId and t.status <> :done and t.deadline >= :now "
            + "order by t.deadline asc, t.id asc")
    List<Task> findNextDue(@Param("userId") long userId, @Param("done") TaskStatus done,
            @Param("now") Instant now, Pageable limit);

    @Query("select t from Task t where t.userId = :userId and t.status <> :done "
            + "order by t.priorityRank desc, t.deadline asc, t.id asc")
    List<Task> findTopPriority(@Param("userId") long userId, @Param("done") TaskStatus done, Pageable limit);
}
