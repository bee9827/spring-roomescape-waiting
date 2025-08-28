package roomescape.service.dto.result;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import roomescape.domain.Theme;

@Builder
public record PopularThemeResult(
        @JsonProperty("theme")
        ThemeResult themeResult,
        Long count
) {
    public PopularThemeResult(Theme theme, Long count) {
        this(ThemeResult.from(theme), count);
    }

    public static PopularThemeResult of(Theme theme, Long count) {
        return new PopularThemeResult(theme, count);
    }
}
