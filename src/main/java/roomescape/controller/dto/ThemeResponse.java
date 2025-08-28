package roomescape.controller.dto;


import lombok.Builder;
import roomescape.service.dto.result.ThemeResult;

@Builder
public record ThemeResponse(
        Long id,
        String name,
        String description,
        String thumbnail
) {
    public static ThemeResponse from(ThemeResult themeResult) {
        return ThemeResponse.builder()
                .id(themeResult.id())
                .name(themeResult.name())
                .description(themeResult.description())
                .thumbnail(themeResult.thumbnail())
                .build();
    }
}
