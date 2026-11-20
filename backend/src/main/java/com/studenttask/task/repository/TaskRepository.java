package com.studenttask.task.repository;

import com.studenttask.task.entity.Task;
import java.util.List;
import java.util.Optional;
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
}
