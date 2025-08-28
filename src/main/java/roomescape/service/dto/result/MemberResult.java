package roomescape.service.dto.result;

import lombok.Builder;
import roomescape.domain.Member;
import roomescape.domain.Role;

@Builder
public record MemberResult(
        Long id,
        String email,
        String name,
        Role role
) {
    public static MemberResult from(Member member) {
        return MemberResult.builder()
                .id(member.getId())
                .email(member.getEmail())
                .name(member.getName())
                .role(member.getRole())
                .build();
    }
}
