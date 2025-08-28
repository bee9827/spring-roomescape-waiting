package roomescape.service.dto.result;

import lombok.Builder;
import roomescape.domain.Theme;

@Builder
public record ThemeResult(
        Long id,
        String name,
        String description,
        String thumbnail
) {
    public static ThemeResult from(Theme theme) {
        return ThemeResult.builder()
                .id(theme.getId())
                .name(theme.getName())
                .description(theme.getDescription())
                .thumbnail(theme.getThumbnail())
                .build();
    }
}
