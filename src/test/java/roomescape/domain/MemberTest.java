package roomescape.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import roomescape.common.exception.RestApiException;
import roomescape.common.exception.status.MemberErrorStatus;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberTest {

    @ParameterizedTest
    @DisplayName("Name 길이가 맞지 않으면 예외를 던진다.")
    @CsvSource(value = {"1", "10글자를넘는이름생성"})
    @NullSource
    void invalidNameLength(String name) {
        assertThatThrownBy(
                () -> Member.builder()
                        .name(name)
                        .email("email@email.com")
                        .password("password")
                        .role(Role.USER)
                        .build())
                .isInstanceOf(RestApiException.class)
                .hasMessage(MemberErrorStatus.INVALID_NAME_LENGTH.getMessage());
    }
}