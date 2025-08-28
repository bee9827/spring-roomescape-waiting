package roomescape.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import roomescape.common.TokenProvider;
import roomescape.controller.dto.LoginRequest;
import roomescape.controller.dto.LoginResponse;
import roomescape.service.AuthService;
import roomescape.common.resolver.AuthMember;
import roomescape.common.util.CookieUtil;

@RestController
@RequiredArgsConstructor
public class JwtApiController {
    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<Void> login(
            @RequestBody LoginRequest loginRequest, HttpServletResponse response) {
        Cookie cookie = CookieUtil.createSessionCookie(TokenProvider.NAME, authService.createToken(loginRequest));
        response.addCookie(cookie);

        return ResponseEntity.ok().build();

    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        Cookie deletedCookie = CookieUtil.deleteCookie(TokenProvider.NAME);
        response.addCookie(deletedCookie);  // max-age: 0
        return ResponseEntity.ok().build();
    }

    @GetMapping("/login/check")
    public ResponseEntity<LoginResponse> check(@AuthMember Long authMemberId) {
        return ResponseEntity.ok(LoginResponse.from(authService.getMember(authMemberId)));
    }

    /*
    Todo List
        [Post] logout
        [Post] login
        [Get] login/check
     */
}
