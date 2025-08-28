package roomescape.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;


@AllArgsConstructor
@Getter
public enum Role {
    ADMIN,
    USER,
    ;

    public static boolean isAdmin(Role role){
        return role == Role.ADMIN;
    }
}
