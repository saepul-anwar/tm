package com.studenttask.task.dto;

import com.studenttask.common.error.AppException;
import com.studenttask.common.error.ErrorCode;
import org.springframework.data.domain.Sort;

/**
 * Allow-list of sortable fields. The query parameter is only ever matched against these names;
 * the entity property used for ordering comes from here, never from user input.
 */
public enum TaskSortField {
    DEADLINE("deadline"),
    PRIORITY("priorityRank"),
    CREATED_AT("createdAt"),
    COMPLETED_AT("completedAt");

    private final String property;

    TaskSortField(String property) {
        this.property = property;
    }

    public static TaskSortField parse(String value) {
        if (value == null) {
            return DEADLINE;
        }
        return switch (value) {
            case "deadline" -> DEADLINE;
            case "priority" -> PRIORITY;
            case "createdAt" -> CREATED_AT;
            case "completedAt" -> COMPLETED_AT;
            default -> throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Parameter sort harus salah satu dari: deadline, priority, createdAt, completedAt.");
        };
    }

    /** Primary order, then deadline asc and id asc so equal keys page deterministically. */
    public Sort toSort(Sort.Direction direction) {
        Sort primary = Sort.by(direction, property);
        if (this != DEADLINE) {
            primary = primary.and(Sort.by(Sort.Direction.ASC, "deadline"));
        }
        return primary.and(Sort.by(Sort.Direction.ASC, "id"));
    }
}
