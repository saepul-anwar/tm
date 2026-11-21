package com.studenttask.task.service;

import com.studenttask.user.repository.UserRepository;
import java.time.DateTimeException;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/** Resolves the zone used for deadline categories: users.timezone, or Asia/Jakarta if missing/invalid. */
@Component
public class UserZoneResolver {

    static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Jakarta");

    private final UserRepository users;

    public UserZoneResolver(UserRepository users) {
        this.users = users;
    }

    public ZoneId zoneOf(long userId) {
        return users.findTimezoneById(userId).map(UserZoneResolver::parse).orElse(DEFAULT_ZONE);
    }

    static ZoneId parse(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return DEFAULT_ZONE;
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (DateTimeException e) {
            return DEFAULT_ZONE;
        }
    }
}
