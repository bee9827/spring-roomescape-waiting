package roomescape.controller.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import roomescape.service.dto.command.MemberCreateCommand;

@Builder
public record MemberCreateRequest(
        @Email(message = "이메일 공백") String email,
        @NotBlank(message = "비번 공백") String password,
        @NotBlank(message = "이름 공백") String name
) {
    public MemberCreateCommand toCommand() {
        return MemberCreateCommand.builder()
                .email(email)
                .password(password)
                .name(name)
                .build();
    }
}
