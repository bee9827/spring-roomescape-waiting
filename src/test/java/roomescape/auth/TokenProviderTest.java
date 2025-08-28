package roomescape.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import roomescape.common.TokenProvider;
import roomescape.common.exception.RestApiException;
import roomescape.common.exception.status.AuthErrorStatus;
import roomescape.domain.Role;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


class TokenProviderTest {
    private static final Long DEFAULT_MEMBER_ID = 1L;
    private static final String secretKeyString = "test1234test1234test1234test1234afsfasfasaf12";
    private static final SecretKey secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKeyString));
    private static final TokenProvider tokenProvider = new TokenProvider(secretKeyString, Duration.ofHours(1));

    @Test
    @DisplayName("createToken: 토큰을 생성할 수 있다.")
    void createToken() {
        assertThat(tokenProvider.getMemberId(generateToken())).isEqualTo(DEFAULT_MEMBER_ID);
    }

    @Test
    @DisplayName("getMemberId: 토큰 기반으로 인증 정보를 가져올 수 있다.")
    void getMemberId() {
        String token = tokenProvider.generateToken(DEFAULT_MEMBER_ID, Role.USER);
        assertThat(tokenProvider.getMemberId(token)).isEqualTo(DEFAULT_MEMBER_ID);
    }

    private String generateToken() {
        return Jwts.builder()
                .claim("id", DEFAULT_MEMBER_ID)
                .claim("role", Role.USER)
                .expiration(new Date(new Date().getTime() + Duration.ofHours(1).toMillis()))
                .signWith(secretKey)
                .compact();
    }

    @Nested
    @DisplayName("validate: ")
    class Validate {
        @Test
        @DisplayName("만료된 토큰일 때 예외를 던진다.")
        void validate() {
            String token = generateExpiredToken();
            assertThatThrownBy(() -> tokenProvider.validateToken(token))
                    .isInstanceOf(RestApiException.class)
                    .hasMessage(AuthErrorStatus.INVALID_TOKEN.getMessage());
        }

        @Test
        @DisplayName("다른 시크릿 키를 이용하여 발급된 토큰 이라면 예외를 던진다.")
        void validateSecretKey() {
            SecretKey otherSecretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKeyString + 1234));
            String otherToken = generateToken(otherSecretKey);

            assertThatThrownBy(() -> tokenProvider.validateToken(otherToken))
                    .isInstanceOf(RestApiException.class)
                    .hasMessage(AuthErrorStatus.INVALID_TOKEN.getMessage());
        }


        private String generateExpiredToken() {
            return Jwts.builder()
                    .claim("id", DEFAULT_MEMBER_ID)
                    .claim("role", Role.USER)
                    .expiration(new Date())
                    .signWith(secretKey)
                    .compact();
        }

        private String generateToken(SecretKey secretKey) {
            return Jwts.builder()
                    .claim("id", DEFAULT_MEMBER_ID)
                    .claim("role", Role.USER)
                    .expiration(new Date(new Date().getTime() + Duration.ofHours(1).toMillis()))
                    .signWith(secretKey)
                    .compact();
        }
    }

}