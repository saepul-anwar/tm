package com.studenttask.security;

import com.studenttask.common.error.AppException;
import com.studenttask.common.error.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** The only source of the caller identity; never read a user id from a request. */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return user.id();
        }
        throw new AppException(ErrorCode.UNAUTHENTICATED, "Autentikasi diperlukan.");
    }
}
