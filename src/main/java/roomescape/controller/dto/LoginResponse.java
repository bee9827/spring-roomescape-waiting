package roomescape.controller.dto;

import lombok.Builder;
import roomescape.service.dto.result.MemberResult;

@Builder
public record LoginResponse(
        Long id,
        String email,
        String name
) {
    public static LoginResponse from(MemberResult memberResult) {
        return LoginResponse.builder()
                .id(memberResult.id())
                .email(memberResult.email())
                .name(memberResult.name())
                .build();
    }
}
