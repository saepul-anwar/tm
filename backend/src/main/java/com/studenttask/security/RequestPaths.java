package com.studenttask.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import org.springframework.web.util.UrlPathHelper;

/**
 * Canonical form of the request path used by the security filters. {@code getRequestURI()} is the raw,
 * still percent-encoded string, so comparing it directly lets {@code /api/auth/%6Cogin} slip past a filter while
 * the router decodes it and still reaches the login controller. Here the path is decoded once (exactly as the
 * router does), stripped of {@code ;params}, and made case-insensitive and slash-insensitive. Being more
 * lenient than the router is intentional: a path that merely looks like a protected one is treated as protected.
 */
final class RequestPaths {

    private RequestPaths() {
    }

    static String canonical(HttpServletRequest request) {
        String decoded = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        Deque<String> segments = new ArrayDeque<>();
        for (String segment : decoded.split("/")) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                segments.pollLast();
            } else {
                segments.addLast(segment.toLowerCase(Locale.ROOT));
            }
        }
        return "/" + String.join("/", segments);
    }
}
