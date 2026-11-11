package com.gopick.server.global;

import com.gopick.server.account.exception.UnauthenticatedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

public final class SessionUtil {
    private SessionUtil() {}

    public static Long requireAccountId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session == null
                || !(session.getAttribute("accountId") instanceof Long accountId)) {
            throw new UnauthenticatedException();
        }

        return accountId;
    }
}
