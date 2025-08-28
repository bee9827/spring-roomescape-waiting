package roomescape.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import roomescape.common.exception.RestApiException;
import roomescape.common.exception.status.MemberErrorStatus;

@Entity
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Member {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(unique = true, nullable = false)
    private String name;
    private String email;

    @NotBlank
    private String password;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Builder
    public Member(String name, String email, String password, Role role) {
        validateNameLength(name);
        this.name = name;
        this.email = email;
        this.password = password;
        this.role = role != null ? role : Role.USER;
    }

    private void validateNameLength(String name) {
        if (name == null || name.length() < 2 || name.length() > 10)
            throw new RestApiException(MemberErrorStatus.INVALID_NAME_LENGTH);
    }

    private void validateEmail(String email) {

    }
}
