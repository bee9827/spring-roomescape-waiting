package roomescape.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import roomescape.common.exception.RestApiException;
import roomescape.common.exception.status.ThemeErrorStatus;
import roomescape.domain.Theme;
import roomescape.repository.ReservationRepository;
import roomescape.repository.ThemeRepository;
import roomescape.service.dto.command.ThemeCreateCommand;
import roomescape.service.dto.result.PopularThemeResult;
import roomescape.service.dto.result.ThemeResult;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ThemeService {
    private final ThemeRepository themeRepository;
    private final ReservationRepository reservationRepository;

    public List<ThemeResult> findAll() {
        return themeRepository.findAll()
                .stream()
                .map(ThemeResult::from)
                .toList();
    }

    public ThemeResult findById(Long id) {
        Theme theme = themeRepository.findById(id)
                .orElseThrow(() -> new RestApiException(ThemeErrorStatus.NOT_FOUND));
        return ThemeResult.from(theme);
    }

    public ThemeResult save(ThemeCreateCommand createCommand) {
        Theme theme = save(createCommand.toEntity());

        return ThemeResult.from(theme);
    }

    public List<PopularThemeResult> getPopularThemes() {
        return themeRepository.findPopularThemes();
    }

    public void deleteById(Long id) {
        validateReservationExists(id);

        Theme savedTheme = getById(id);
        themeRepository.delete(savedTheme);
    }

    private Theme save(Theme theme) {
        return themeRepository.save(theme);
    }

    private Theme getById(Long id) {
        return themeRepository.findById(id)
                .orElseThrow(() -> new RestApiException(ThemeErrorStatus.NOT_FOUND));
    }

    private void validateReservationExists(Long id) {
        if (reservationRepository.existsByThemeId(id)) {
            throw new RestApiException(ThemeErrorStatus.RESERVATION_EXIST);
        }
    }
}
