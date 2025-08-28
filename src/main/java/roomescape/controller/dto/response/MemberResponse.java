package roomescape.controller.dto.response;

import lombok.Builder;
import roomescape.service.dto.result.MemberResult;

@Builder
public record MemberResponse(
        Long id,
        String email,
        String name
) {
    public static MemberResponse from(MemberResult memberResult) {
        return MemberResponse.builder()
                .id(memberResult.id())
                .email(memberResult.email())
                .name(memberResult.name())
                .build();
    }
}
