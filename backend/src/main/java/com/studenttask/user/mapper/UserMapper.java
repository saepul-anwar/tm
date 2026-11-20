package com.studenttask.user.mapper;

import com.studenttask.auth.dto.UserProfileResponse;
import com.studenttask.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    /** Never exposes the password hash. */
    public UserProfileResponse toProfile(User user) {
        return new UserProfileResponse(user.getId(), user.getFullName(), user.getEmail(), user.getTimezone());
    }
}
