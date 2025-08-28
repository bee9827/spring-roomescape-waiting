package roomescape.controller.dto;

import roomescape.service.dto.command.ThemeCreateCommand;

public record ThemeCreateRequest(
        String name,
        String description,
        String thumbnail
) {
    public ThemeCreateCommand toCommand() {
        return ThemeCreateCommand.builder()
                .name(name)
                .description(description)
                .thumbnail(thumbnail)
                .build();
    }
}
