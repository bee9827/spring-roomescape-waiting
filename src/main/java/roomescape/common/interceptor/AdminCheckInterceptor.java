package roomescape.common.interceptor;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import roomescape.common.TokenProvider;
import roomescape.common.exception.RestApiException;
import roomescape.common.exception.status.AuthErrorStatus;
import roomescape.common.util.CookieUtil;
import roomescape.domain.Role;

@Component
@RequiredArgsConstructor
public class AdminCheckInterceptor implements HandlerInterceptor {
    private final TokenProvider tokenProvider;

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws Exception {

        try {
            Cookie[] cookies = request.getCookies();
            String token = CookieUtil.getTokenByName(TokenProvider.NAME, cookies);

            Role role = tokenProvider.getRole(token);
            if (Role.isAdmin(role)) return true;

        } catch (RestApiException e) {
            throw new RestApiException(AuthErrorStatus.NOT_AUTHORIZED);
        }
        return false;
    }
}
