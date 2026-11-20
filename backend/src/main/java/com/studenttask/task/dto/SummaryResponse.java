package com.studenttask.task.dto;

import java.util.List;

public record SummaryResponse(
        DashboardCounts counts,
        int completionPercent,
        List<TaskResponse> dueSoon,
        List<TaskResponse> topPriority) {
}
