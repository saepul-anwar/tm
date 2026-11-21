package com.studenttask.task.repository;

import com.studenttask.task.dto.TaskFilter;
import com.studenttask.task.entity.Task;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class TaskSpecifications {

    static final char LIKE_ESCAPE = '\\';

    private TaskSpecifications() {
    }

    /** Always scoped to {@code userId}; every other predicate is optional and bound as a parameter. */
    public static Specification<Task> forUser(long userId, TaskFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            if (hasText(filter.q())) {
                String pattern = "%" + escapeLike(filter.q().trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.like(cb.lower(root.get("title")), pattern, LIKE_ESCAPE));
            }
            if (hasText(filter.subject())) {
                predicates.add(cb.equal(root.get("subject"), filter.subject().trim()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.priority() != null) {
                predicates.add(cb.equal(root.get("priority"), filter.priority()));
            }
            if (filter.deadlineFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("deadline"), filter.deadlineFrom()));
            }
            if (filter.deadlineTo() != null) {
                predicates.add(cb.lessThan(root.get("deadline"), filter.deadlineTo()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Makes {@code %}, {@code _} and the escape character itself match literally. */
    static String escapeLike(String raw) {
        StringBuilder out = new StringBuilder(raw.length() + 4);
        for (char c : raw.toCharArray()) {
            if (c == LIKE_ESCAPE || c == '%' || c == '_') {
                out.append(LIKE_ESCAPE);
            }
            out.append(c);
        }
        return out.toString();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
