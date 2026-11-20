package com.studenttask.task.repository;

import com.studenttask.task.entity.Task;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {

    /** The only lookup by id: ownership is part of the query so another user's task is simply absent. */
    Optional<Task> findByIdAndUserId(Long id, long userId);
}
