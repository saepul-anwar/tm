package com.studenttask.task.dto;

/** {@code overdue} is a subset of {@code todo + inProgress}, not a status; see API_SPECIFICATION. */
public record DashboardCounts(long total, long todo, long inProgress, long completed, long overdue, long upcoming) {
}
